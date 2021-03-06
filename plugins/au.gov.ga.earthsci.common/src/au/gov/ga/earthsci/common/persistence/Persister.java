/*******************************************************************************
 * Copyright 2012 Geoscience Australia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package au.gov.ga.earthsci.common.persistence;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import au.gov.ga.earthsci.common.util.AnnotationUtil;
import au.gov.ga.earthsci.common.util.StringInstantiable;
import au.gov.ga.earthsci.common.util.Util;
import au.gov.ga.earthsci.common.util.XmlUtil;

/**
 * Persists annotated {@link Exportable} types.
 * 
 * @see Exportable
 * @see Persistent
 * @see Adapter
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public class Persister
{
	protected static final String TYPE_ATTRIBUTE = "type"; //$NON-NLS-1$
	protected static final String NULL_ATTRIBUTE = "null"; //$NON-NLS-1$
	protected static final String DEFAULT_ARRAY_ELEMENT_NAME = "element"; //$NON-NLS-1$

	private final Map<String, Class<?>> nameToExportable = new HashMap<String, Class<?>>();
	private final Map<Class<?>, String> exportableToName = new HashMap<Class<?>, String>();
	private final Map<Class<?>, IPersistentAdapter<?>> adapters = new HashMap<Class<?>, IPersistentAdapter<?>>();
	private final Set<ClassLoader> classLoaders = new HashSet<ClassLoader>();

	private boolean ignoreMissing = false;
	private boolean ignoreNulls = false;

	/**
	 * @return Should this {@link Persister} ignore missing XML
	 *         elements/attributes during unpersisting?
	 * @see #setIgnoreMissing(boolean)
	 */
	public boolean isIgnoreMissing()
	{
		return ignoreMissing;
	}

	/**
	 * Sets if this {@link Persister} should ignore missing XML
	 * elements/attributes for fields/methods marked as {@link Persistent} in
	 * the {@link Exportable}s that it loads.
	 * <p/>
	 * The default behaviour is, if a field/method is marked persistent, and an
	 * XML node does not exist in the XML for that {@link Exportable}, a
	 * {@link PersistenceException} is thrown. However, if
	 * {@link #isIgnoreMissing()} is true, this is ignored and the method/field
	 * is not called/set.
	 * 
	 * @param ignoreMissing
	 */
	public void setIgnoreMissing(boolean ignoreMissing)
	{
		this.ignoreMissing = ignoreMissing;
	}

	/**
	 * @return Should this {@link Persister} ignore null {@link Persistent}
	 *         values when persisting?
	 * @see #setIgnoreNulls(boolean)
	 */
	public boolean isIgnoreNulls()
	{
		return ignoreNulls;
	}

	/**
	 * Sets if this {@link Persister} should ignore null values for
	 * methods/fields that are marked {@link Persistent}.
	 * <p/>
	 * The default behaviour is, if a field/method is marked persistent, and is
	 * null, it will be saved as an element with an attribute null="true".
	 * <p/>
	 * If this property is true, then {@link #setIgnoreMissing(boolean)} should
	 * also be set to true; otherwise an exception will be thrown for missing
	 * elements for null values.
	 * 
	 * @param ignoreNulls
	 */
	public void setIgnoreNulls(boolean ignoreNulls)
	{
		this.ignoreNulls = ignoreNulls;
	}

	/**
	 * Register a name for a given {@link Exportable} type. This name is used
	 * for the top level XML element name (instead of the canonical class name)
	 * when persisting objects of this type.
	 * 
	 * @param type
	 *            Class of the type to name
	 * @param name
	 *            XML element name to use when persisting type objects
	 */
	public void registerNamedExportable(Class<?> type, String name)
	{
		try
		{
			assertIsExportable(type);
		}
		catch (PersistenceException e)
		{
			throw new IllegalArgumentException(e);
		}
		if (Util.isEmpty(name))
		{
			throw new IllegalArgumentException("name must not be empty"); //$NON-NLS-1$
		}
		if (!name.matches("^\\w+$")) //$NON-NLS-1$
		{
			throw new IllegalArgumentException("name must only be word characters"); //$NON-NLS-1$
		}
		nameToExportable.put(name, type);
		exportableToName.put(type, name);
	}

	/**
	 * Unregister a named {@link Exportable} type.
	 * 
	 * @param type
	 *            Type to unregister
	 * @see #registerNamedExportable(Class, String)
	 */
	public void unregisterNamedExportable(Class<?> type)
	{
		String name = exportableToName.remove(type);
		nameToExportable.remove(name);
	}

	/**
	 * Unregister a named {@link Exportable} name.
	 * 
	 * @param name
	 *            Name to unregister
	 * @see #registerNamedExportable(Class, String)
	 */
	public void unregisterNamedExportable(String name)
	{
		Class<?> type = nameToExportable.remove(name);
		exportableToName.remove(type);
	}

	/**
	 * Register an {@link IPersistentAdapter} to use when persisting objects of
	 * the given type. This overrides any {@link Adapter} annotations for
	 * fields/methods of this type.
	 * 
	 * @param type
	 *            Type that the adapter supports
	 * @param adapter
	 *            Adapter used for persisting type objects
	 */
	public <E> void registerAdapter(Class<E> type, IPersistentAdapter<E> adapter)
	{
		adapters.put(type, adapter);
	}

	/**
	 * Unregister the {@link IPersistentAdapter} registered for the given type.
	 * 
	 * @param type
	 *            Type of {@link IPersistentAdapter} to unregister
	 * @see #registerAdapter(Class, IPersistentAdapter)
	 */
	public void unregisterAdapter(Class<?> type)
	{
		adapters.remove(type);
	}

	/**
	 * Register a {@link ClassLoader} that can be used for resolving classes
	 * from class names. This is required if the caller resides in a different
	 * plugin, which means this plugin's classloader doesn't have access to the
	 * caller plugin's classes.
	 * 
	 * @param classLoader
	 *            ClassLoader to register
	 */
	public void registerClassLoader(ClassLoader classLoader)
	{
		classLoaders.add(classLoader);
	}

	/**
	 * Unregister a registered {@link ClassLoader}.
	 * 
	 * @param classLoader
	 *            ClassLoader to unregister
	 * @see #registerClassLoader(ClassLoader)
	 */
	public void unregisterClassLoader(ClassLoader classLoader)
	{
		classLoaders.remove(classLoader);
	}

	/**
	 * Save the given {@link Exportable} object to XML under the given parent.
	 * 
	 * @param o
	 *            Object to save/persist
	 * @param parent
	 *            XML element to save inside
	 * @param context
	 * @throws PersistenceException
	 *             If an error occurs during persistance of the object
	 */
	public void save(Object o, Element parent, URI context) throws PersistenceException
	{
		if (o == null)
		{
			throw new NullPointerException("Object cannot be null"); //$NON-NLS-1$
		}
		if (parent == null)
		{
			throw new NullPointerException("Parent element cannot be null"); //$NON-NLS-1$
		}

		assertIsExportable(o.getClass());
		String elementName = getNameFromType(o.getClass());

		Element element = parent.getOwnerDocument().createElement(elementName);
		parent.appendChild(element);

		persistMethods(o, element, context);
		persistFields(o, element, context);
	}

	/**
	 * Persist the {@link Persistent} methods of the given object.
	 * 
	 * @param o
	 *            Object whose method values should be persisted
	 * @param element
	 *            XML element to save inside
	 * @param context
	 * @throws PersistenceException
	 */
	protected void persistMethods(Object o, Element element, URI context) throws PersistenceException
	{
		Method[] methods = AnnotationUtil.getAnnotatedMethods(o.getClass(), Persistent.class);
		for (Method method : methods)
		{
			method.setAccessible(true);
			Persistent persistent = AnnotationUtil.getAnnotation(method, Persistent.class);
			String name = checkAndGetPersistentName(method, persistent);
			Object value;
			try
			{
				value = method.invoke(o);
			}
			catch (Exception e)
			{
				throw new PersistenceException(e);
			}

			Adapter adapter = AnnotationUtil.getAnnotation(method, Adapter.class);
			persist(value, method.getReturnType(), name, element, context, persistent, adapter);
		}
	}

	/**
	 * Persist the {@link Persistent} fields of the given object.
	 * 
	 * @param o
	 *            Object whose fields should be persisted
	 * @param element
	 *            XML element to save inside
	 * @param context
	 * @throws PersistenceException
	 */
	protected void persistFields(Object o, Element element, URI context) throws PersistenceException
	{
		Field[] fields = AnnotationUtil.getAnnotatedFields(o.getClass(), Persistent.class);
		for (Field field : fields)
		{
			field.setAccessible(true);
			Persistent persistent = AnnotationUtil.getAnnotation(field, Persistent.class);
			String name = checkAndGetPersistentName(field, persistent);
			Object value;
			try
			{
				value = field.get(o);
			}
			catch (Exception e)
			{
				throw new PersistenceException(e);
			}

			Adapter adapter = AnnotationUtil.getAnnotation(field, Adapter.class);
			persist(value, field.getType(), name, element, context, persistent, adapter);
		}
	}

	/**
	 * Persist a value into an element (or attribute) with the given name.
	 * <p/>
	 * If the type of the value is a subclass of the given baseType, then the
	 * classname is also persisted. This allows the {@link Persister} to know
	 * what type to instantiate when loading.
	 * 
	 * @param value
	 *            Value to persist
	 * @param baseType
	 *            Type specified by the method/field (can be null)
	 * @param name
	 *            XML element (or attribute) name to save to
	 * @param element
	 *            XML element to save inside
	 * @param context
	 * @param persistent
	 *            Field/method's {@link Persistent} annotation
	 * @param adapter
	 *            Field/method's {@link Adapter} annotation
	 * @throws PersistenceException
	 */
	protected void persist(Object value, Class<?> baseType, String name, Element element, URI context,
			Persistent persistent, Adapter adapter) throws PersistenceException
	{
		//if should ignore nulls and this value is null, don't create an element
		if (isIgnoreNulls() && value == null)
		{
			return;
		}

		Element nameElement = element.getOwnerDocument().createElement(name);
		element.appendChild(nameElement);

		//if the value is null, mark it as such with an attribute on the element, and return
		if (value == null)
		{
			nameElement.setAttribute(NULL_ATTRIBUTE, Boolean.TRUE.toString());
			return;
		}

		IPersistentAdapter<?> persistentAdapter = getAdapter(value.getClass(), adapter);
		boolean isExportable = AnnotationUtil.getAnnotation(value.getClass(), Exportable.class) != null;

		//if the value type isn't the same as the type specified by the field/method, and
		//it isn't a boxed version, then save the type as an attribute on the element
		boolean classNameSaved = false;
		if (!value.getClass().equals(baseType))
		{
			boolean boxed =
					baseType != null && baseType.isPrimitive()
							&& Util.primitiveClassToBoxed(baseType).equals(value.getClass());
			boolean isBoxedOrAdapterOrExportable =
					boxed || (adapter != null && persistentAdapter != null) || isExportable;
			if (value instanceof Collection<?> || !isBoxedOrAdapterOrExportable)
			{
				nameElement.setAttribute(TYPE_ATTRIBUTE, getNameFromType(value.getClass()));
				classNameSaved = true;
			}
		}

		//If the value is an array or Collection, save each element as a separate XML element
		if (value.getClass().isArray() || value instanceof Collection<?>)
		{
			if (persistent.attribute())
			{
				throw new PersistenceException("Array or collection Persistent cannot be an attribute"); //$NON-NLS-1$
			}

			String arrayElementName = getArrayElementName(persistent);

			if (value.getClass().isArray())
			{
				for (int i = 0; i < Array.getLength(value); i++)
				{
					Object arrayElement = Array.get(value, i);
					Class<?> componentType = baseType == null ? null : baseType.getComponentType();
					persist(arrayElement, componentType, arrayElementName, nameElement, context, persistent, adapter);
				}
			}
			else
			{
				Collection<?> collection = (Collection<?>) value;
				for (Object collectionElement : collection)
				{
					persist(collectionElement, null, arrayElementName, nameElement, context, persistent, adapter);
				}
			}
			return;
		}

		if (persistentAdapter != null)
		{
			//if there's a IPersistentAdapter for this object's type, use it to create the XML
			@SuppressWarnings("unchecked")
			IPersistentAdapter<Object> objectAdapter = (IPersistentAdapter<Object>) persistentAdapter;
			objectAdapter.toXML(value, nameElement, context);
		}
		else if (isExportable)
		{
			//if the object is itself exportable, recurse
			save(value, nameElement, context);
		}
		else
		{
			//once here, the only objects supported for persistance are those that are StringInstantiable
			assertIsStringInstantiable(value.getClass());
			String stringValue = StringInstantiable.toString(value);

			if (persistent.attribute() && !classNameSaved)
			{
				element.removeChild(nameElement);
				element.setAttribute(name, stringValue);
			}
			else
			{
				Text text = nameElement.getOwnerDocument().createTextNode(stringValue);
				nameElement.appendChild(text);
			}
		}
	}

	/**
	 * Load an {@link Exportable} object from an XML element.
	 * 
	 * @param element
	 *            Element to load from
	 * @param context
	 * @return New object loaded from XML
	 * @throws PersistenceException
	 *             If an error occurs during persistance of the object
	 */
	public Object load(Element element, URI context) throws PersistenceException
	{
		if (element == null)
		{
			throw new NullPointerException("Element cannot be null"); //$NON-NLS-1$
		}

		Class<?> c = getTypeFromName(element.getTagName());
		assertIsExportable(c);

		IPersistentAdapter<?> adapter = getAdapter(c, AnnotationUtil.getAnnotation(c, Adapter.class));
		if (adapter != null)
		{
			@SuppressWarnings("unchecked")
			IPersistentAdapter<Object> objectAdapter = (IPersistentAdapter<Object>) adapter;
			return objectAdapter.fromXML(element, context);
		}

		Constructor<?> constructor = null;
		try
		{
			constructor = c.getDeclaredConstructor();
		}
		catch (NoSuchMethodException e)
		{
			//impossible; already checked
		}
		constructor.setAccessible(true);

		Object o;
		try
		{
			o = constructor.newInstance();
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}

		unpersistMethods(o, element, context);
		unpersistFields(o, element, context);

		return o;
	}

	/**
	 * Unpersist the {@link Persistent} methods on the given object from an XML
	 * element.
	 * 
	 * @param o
	 *            Object to unpersist methods to
	 * @param element
	 *            XML element to unpersist
	 * @param context
	 * @throws PersistenceException
	 */
	protected void unpersistMethods(Object o, Element element, URI context) throws PersistenceException
	{
		Method[] methods = AnnotationUtil.getAnnotatedMethods(o.getClass(), Persistent.class);
		for (Method method : methods)
		{
			method.setAccessible(true);
			Persistent persistent = AnnotationUtil.getAnnotation(method, Persistent.class);
			String name = checkAndGetPersistentName(method, persistent);
			String methodName = removeGetter(method);
			Class<?> type = method.getReturnType();
			Method setter = getSetter(o.getClass(), methodName, type, persistent);

			Adapter adapter = AnnotationUtil.getAnnotation(method, Adapter.class);
			try
			{
				Object value = unpersist(0, element, name, type, context, persistent, adapter);
				setter.invoke(o, value);
			}
			catch (MissingPersistentException e)
			{
				if (!isIgnoreMissing())
				{
					throw e;
				}
			}
			catch (PersistenceException e)
			{
				throw e;
			}
			catch (Exception e)
			{
				throw new PersistenceException(e);
			}
		}
	}

	/**
	 * Unpersist the {@link Persistent} fields for the given object from an XML
	 * element.
	 * 
	 * @param o
	 *            Object to unpersist fields to
	 * @param element
	 *            XML element to unpersist
	 * @param context
	 * @throws PersistenceException
	 */
	protected void unpersistFields(Object o, Element element, URI context) throws PersistenceException
	{
		Field[] fields = AnnotationUtil.getAnnotatedFields(o.getClass(), Persistent.class);
		for (Field field : fields)
		{
			field.setAccessible(true);
			Persistent persistent = AnnotationUtil.getAnnotation(field, Persistent.class);
			String name = checkAndGetPersistentName(field, persistent);
			Class<?> type = field.getType();

			Adapter adapter = AnnotationUtil.getAnnotation(field, Adapter.class);
			try
			{
				Object value = unpersist(0, element, name, type, context, persistent, adapter);
				field.set(o, value);
			}
			catch (MissingPersistentException e)
			{
				if (!isIgnoreMissing())
				{
					throw e;
				}
			}
			catch (PersistenceException e)
			{
				throw e;
			}
			catch (Exception e)
			{
				throw new PersistenceException(e);
			}
		}
	}

	/**
	 * Load/unpersist an object from an XML element (or attribute) with the
	 * given name.
	 * 
	 * @param index
	 *            Index of the element within the list of direct child elements
	 *            of parent with the given tag name
	 * @param parent
	 *            XML element in which to search for child elements (or an
	 *            attribute) with the given tag name
	 * @param name
	 *            XML element (or attribute) name that stores the value to
	 *            unpersist
	 * @param type
	 *            Type to unpersist to (can be null if the element has an
	 *            attribute which specifies the type)
	 * @param context
	 * @param persistent
	 *            Field/method's {@link Persistent} annotation
	 * @param adapter
	 *            Field/method's {@link Adapter} annotation
	 * @return New object loaded from XML
	 * @throws PersistenceException
	 */
	protected Object unpersist(int index, Element parent, String name, Class<?> type, URI context,
			Persistent persistent, Adapter adapter) throws PersistenceException
	{
		//get the index'th named element of parent
		Element element = XmlUtil.getChildElementByTagName(index, name, parent);
		Attr attribute = parent.getAttributeNode(name);

		if (element != null)
		{
			//if the null attribute is set, return null
			String nullAttribute = element.getAttribute(NULL_ATTRIBUTE);
			if (Boolean.valueOf(nullAttribute))
			{
				return null;
			}

			//the className attribute can override the type (to support subclasses)
			String classNameAttribute = element.getAttribute(TYPE_ATTRIBUTE);
			if (!Util.isEmpty(classNameAttribute))
			{
				//for each [] at the end of the class name, increment the array depth
				int arrayDepth = 0;
				while (classNameAttribute.endsWith("[]")) //$NON-NLS-1$
				{
					classNameAttribute = classNameAttribute.substring(0, classNameAttribute.length() - 2);
					arrayDepth++;
				}
				//load the class from the name
				type = getTypeFromName(classNameAttribute, false);
				if (type == null)
				{
					type = getTypeFromName(element.getTagName(), false);
				}
				if (type != null)
				{
					//make the type an array type with the correct depth
					while (arrayDepth > 0)
					{
						type = Array.newInstance(type, 0).getClass();
						arrayDepth--;
					}
				}
			}
		}

		IPersistentAdapter<?> persistentAdapter = getAdapter(type, adapter);
		//if there is no type, and no adapter, the first element must be exportable
		if (type == null && persistentAdapter == null)
		{
			Element firstChild = element == null ? null : XmlUtil.getFirstChildElement(element);
			if (firstChild == null)
			{
				throw new PersistenceException("Unpersist type is null"); //$NON-NLS-1$
			}

			//if the type isn't defined, assume the first child element is exportable
			type = getTypeFromName(firstChild.getTagName());
			assertIsExportable(type);
			persistentAdapter = getAdapter(type, adapter);
		}

		//handle array/collection types
		if (type != null && (type.isArray() || Collection.class.isAssignableFrom(type)))
		{
			if (element == null)
			{
				throw new PersistenceException("Could not find element for name: " + name); //$NON-NLS-1$
			}

			//calculate the array length from the number of child elements
			String arrayElementName = getArrayElementName(persistent);
			int length = XmlUtil.getCountChildElementsByTagName(arrayElementName, element);

			if (type.isArray())
			{
				//create an array and unpersist the elements into it
				Object array = Array.newInstance(type.getComponentType(), length);
				for (int i = 0; i < length; i++)
				{
					//recurse
					Object o =
							unpersist(i, element, arrayElementName, type.getComponentType(), context, persistent,
									adapter);
					Array.set(array, i, o);
				}
				return array;
			}
			else
			{
				//instantiate the collection implementation
				String collectionClassName = element.getAttribute(TYPE_ATTRIBUTE);
				Class<?> collectionType;
				if (Util.isEmpty(collectionClassName))
				{
					if (Modifier.isAbstract(type.getModifiers()) || type.isInterface())
					{
						throw new PersistenceException("Collection class not specified"); //$NON-NLS-1$
					}
					collectionType = type;
				}
				else
				{
					collectionType = getTypeFromName(collectionClassName);
				}
				Collection<Object> collection;
				try
				{
					Constructor<?> constructor = collectionType.getConstructor();
					@SuppressWarnings("unchecked")
					Collection<Object> objectCollection = (Collection<Object>) constructor.newInstance();
					collection = objectCollection;
				}
				catch (Exception e)
				{
					throw new PersistenceException("Error instantiating collection", e); //$NON-NLS-1$
				}
				//unpersist the collection's elements
				for (int i = 0; i < length; i++)
				{
					//recurse
					//we don't know the type for collection elements (they must specify the className attribute)
					Object o = unpersist(i, element, arrayElementName, null, context, persistent, adapter);
					collection.add(o);
				}
				return collection;
			}
		}

		String stringValue = null;

		if (element != null)
		{
			if (persistentAdapter != null)
			{
				//if there's a IPersistentAdapter for this object's type, use it to load the XML
				@SuppressWarnings("unchecked")
				IPersistentAdapter<Object> objectAdapter = (IPersistentAdapter<Object>) persistentAdapter;
				return objectAdapter.fromXML(element, context);
			}
			else
			{
				Element child = XmlUtil.getFirstChildElement(element);
				if (child != null)
				{
					//assume, if there's a child element, the type is exportable: recurse
					return load(child, context);
				}
				else
				{
					Text text = XmlUtil.getFirstChildText(element);
					if (text == null)
					{
						throw new PersistenceException("No text child found"); //$NON-NLS-1$
					}
					stringValue = text.getData();
				}
			}
		}
		else if (attribute != null)
		{
			stringValue = attribute.getValue();
		}

		//if context is non-null, use it to resolve relative URIs/URLs
		if (context != null)
		{
			if (URI.class.isAssignableFrom(type))
			{
				try
				{
					return context.resolve(new URI(stringValue));
				}
				catch (URISyntaxException e)
				{
					throw new PersistenceException("Error converting string to URI", e); //$NON-NLS-1$
				}
			}
			if (URL.class.isAssignableFrom(type))
			{
				try
				{
					return new URL(context.toURL(), stringValue);
				}
				catch (MalformedURLException e)
				{
					throw new PersistenceException("Error converting string to URL", e); //$NON-NLS-1$
				}
			}
		}

		//once here, the only objects supported for unpersistance are those that are StringInstantiable
		if (stringValue != null)
		{
			assertIsStringInstantiable(type);
			return StringInstantiable.newInstance(stringValue, type);
		}

		//if we get here, there's no element/attribute for the given Persistent
		throw new MissingPersistentException("Could not unpersist Persistable: " + name); //$NON-NLS-1$
	}

	/**
	 * Check that the method is persistable (no parameters, and a non-void
	 * return type), and calculate the element/attribute name to save to.
	 * 
	 * @param method
	 *            Method that will be persisted
	 * @param persistent
	 *            Method's {@link Persistent} annotation
	 * @return Element/attribute name for the given method
	 * @throws PersistenceException
	 */
	protected String checkAndGetPersistentName(Method method, Persistent persistent) throws PersistenceException
	{
		if (method.getParameterTypes().length > 0)
		{
			throw new PersistenceException("Cannot persist parameterized methods: " + method); //$NON-NLS-1$
		}
		if (void.class.equals(method.getReturnType()))
		{
			throw new PersistenceException("Cannot persist methods with no return type: " + method); //$NON-NLS-1$
		}
		String name = persistent.name();
		if (Util.isEmpty(name))
		{
			name = removeGetter(method);
		}
		if (Util.isEmpty(name))
		{
			throw new PersistenceException("Could not determine name for method: " + method); //$NON-NLS-1$
		}
		return name;
	}

	/**
	 * Calculate the element/attribute name to save the given field to.
	 * 
	 * @param field
	 *            Field that will be persisted
	 * @param persistent
	 *            Field's {@link Persistent} annotation
	 * @return Element/attribute name for the given field
	 * @throws PersistenceException
	 */
	protected String checkAndGetPersistentName(Field field, Persistent persistent) throws PersistenceException
	{
		String name = persistent.name();
		if (Util.isEmpty(name))
		{
			name = field.getName();
		}
		if (Util.isEmpty(name))
		{
			throw new PersistenceException("Could not determine name for field: " + field); //$NON-NLS-1$
		}
		return name;
	}

	/**
	 * Remove the 'get' (or 'is' for boolean return types) method name prefix
	 * (if it exists), and lowercase the first character (if the prefix was
	 * present).
	 * 
	 * @param method
	 *            Method from which to remove the 'get'/'is' prefix from
	 * @return Method name without the 'get'/'is' prefix
	 */
	protected String removeGetter(Method method)
	{
		String name = method.getName();
		if (boolean.class.equals(method.getReturnType()) && name.length() > 2 && name.startsWith("is")) //$NON-NLS-1$
		{
			name = name.substring(2, 3).toLowerCase() + name.substring(3);
		}
		else if (name.length() > 3 && name.startsWith("get")) //$NON-NLS-1$
		{
			name = name.substring(3, 4).toLowerCase() + name.substring(4);
		}
		return name;
	}

	/**
	 * Find the setter method in the class for the given property name. If the
	 * {@link Persistent} annotation defines the setter property, then return
	 * the method with that name.
	 * 
	 * @param c
	 *            Class in which to find the setter method
	 * @param name
	 *            Name of the property to find a setter for (ignored if the
	 *            {@link Persistent} annotation defines the setter)
	 * @param parameterType
	 *            Type that the setter method should have a single parameter for
	 * @param persistent
	 *            {@link Persistent} annotation for the corresponding getter
	 * @return
	 * @throws PersistenceException
	 */
	protected Method getSetter(Class<?> c, String name, Class<?> parameterType, Persistent persistent)
			throws PersistenceException
	{
		if (!Util.isEmpty(persistent.setter()))
		{
			try
			{
				return getSetterMethod(c, persistent.setter(), parameterType);
			}
			catch (NoSuchMethodException e)
			{
				throw new PersistenceException("Cannot find matching Persistent setter: " + persistent.setter() //$NON-NLS-1$
						+ " in class " + c, e); //$NON-NLS-1$
			}
		}

		if (Util.isEmpty(name))
		{
			throw new PersistenceException("Persistent setter name is empty"); //$NON-NLS-1$
		}

		//first find a method with the property name and a 'set' prefix (ie if property = name, setter = setName)
		String setName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1); //$NON-NLS-1$
		try
		{
			return getSetterMethod(c, setName, parameterType);
		}
		catch (NoSuchMethodException e)
		{
		}

		//next try and find a method that is just named the property name
		try
		{
			return getSetterMethod(c, name, parameterType);
		}
		catch (NoSuchMethodException e)
		{
		}

		throw new PersistenceException("Cannot find matching Persistent setter: " + setName + " in class " + c); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Find a setter method declared in a class with the given name. If not
	 * found in the class, recurses to search the class' superclasses and
	 * implemented interfaces.
	 * 
	 * @param c
	 *            Class to search for the setter method
	 * @param name
	 *            Name of the setter method
	 * @param parameterType
	 *            Single parameter type that the setter accepts
	 * @return Setter method
	 * @throws NoSuchMethodException
	 *             If a corresponding setter method could not be found
	 */
	protected Method getSetterMethod(Class<?> c, String name, Class<?> parameterType) throws NoSuchMethodException
	{
		NoSuchMethodException noSuchMethodException;
		try
		{
			Method m = c.getDeclaredMethod(name, parameterType);
			m.setAccessible(true);
			return m;
		}
		catch (NoSuchMethodException e)
		{
			noSuchMethodException = e;
		}
		//search super class
		if (c.getSuperclass() != null)
		{
			try
			{
				return getSetterMethod(c.getSuperclass(), name, parameterType);
			}
			catch (NoSuchMethodException e)
			{
			}
		}
		//search interfaces
		for (Class<?> i : c.getInterfaces())
		{
			try
			{
				return getSetterMethod(i, name, parameterType);
			}
			catch (NoSuchMethodException e)
			{
			}
		}
		//could not be found, throw the original exception
		throw noSuchMethodException;
	}

	/**
	 * Calculate the XML element name to use for array elements. If the
	 * {@link Persistent} attribute defines an element name, return that,
	 * otherwise return the default.
	 * 
	 * @param persistent
	 *            {@link Persistent} annotation that may define the element name
	 * @return The XML element name to use for array elements
	 */
	protected String getArrayElementName(Persistent persistent)
	{
		String arrayElementName = persistent.elementName();
		if (Util.isEmpty(arrayElementName))
		{
			arrayElementName = DEFAULT_ARRAY_ELEMENT_NAME;
		}
		return arrayElementName;
	}

	/**
	 * Get the {@link IPersistentAdapter} used to persist the given type to XML.
	 * If the type already has a registered adapter (from
	 * {@link #registerAdapter(Class, IPersistentAdapter)}), return that;
	 * otherwise, if the {@link Adapter} annotation is non-null, instantiate and
	 * return an object of the class defined in the annotation.
	 * 
	 * @param type
	 *            Type for which to get an adapter for
	 * @param adapter
	 *            Adapter annotation
	 * @return {@link IPersistentAdapter} used to persist the given type
	 * @throws PersistenceException
	 */
	protected IPersistentAdapter<?> getAdapter(Class<?> type, Adapter adapter) throws PersistenceException
	{
		IPersistentAdapter<?> persistentAdapter = type != null ? adapters.get(type) : null;
		if (persistentAdapter == null && adapter != null)
		{
			Class<? extends IPersistentAdapter<?>> adapterClass = adapter.value();
			if (adapterClass != null)
			{
				try
				{
					Constructor<? extends IPersistentAdapter<?>> constructor = adapterClass.getDeclaredConstructor();
					constructor.setAccessible(true);
					persistentAdapter = constructor.newInstance();
				}
				catch (Exception e)
				{
					throw new PersistenceException("Error instantiating adapter class: " + adapterClass, e); //$NON-NLS-1$
				}
			}
		}
		return persistentAdapter;
	}

	/**
	 * Calculate the type for the given name. If the name has been registered
	 * using {@link #registerNamedExportable(Class, String)}, that type is
	 * returned. Otherwise {@link ClassLoader#loadClass(String)} is used.
	 * 
	 * @param name
	 *            Name to calculate type for
	 * @return Type for name
	 * @throws PersistenceException
	 */
	protected Class<?> getTypeFromName(String name) throws PersistenceException
	{
		return getTypeFromName(name, true);
	}

	private Class<?> getTypeFromName(String name, boolean failHard) throws PersistenceException
	{
		Class<?> c = nameToExportable.get(name);
		if (c != null)
		{
			return c;
		}
		c = PrimitiveNames.NAME_TO_PRIMITIVE.get(name);
		if (c != null)
		{
			return c;
		}
		name = name.replace('-', '$');
		try
		{
			return getClass().getClassLoader().loadClass(name);
		}
		catch (ClassNotFoundException e)
		{
		}
		for (ClassLoader classLoader : classLoaders)
		{
			try
			{
				return classLoader.loadClass(name);
			}
			catch (ClassNotFoundException e)
			{
			}
		}
		if (failHard)
		{
			throw new PersistenceException("Could not determine type for name: " + name); //$NON-NLS-1$
		}
		else
		{
			return null;
		}
	}

	/**
	 * Calculate the name for the given type. If the type is marked as
	 * {@link Exportable} and a named exportable has been registered using
	 * {@link #registerNamedExportable(Class, String)}, that name is returned.
	 * Otherwise the canonical class name is returned.
	 * 
	 * @param type
	 *            Type to calculate name for
	 * @return Name of type
	 * @throws PersistenceException
	 */
	protected String getNameFromType(Class<?> type) throws PersistenceException
	{
		String name = exportableToName.get(type);
		if (Util.isEmpty(name))
		{
			name = PrimitiveNames.PRIMITIVE_TO_NAME.get(type);
		}
		if (Util.isEmpty(name))
		{
			//we want the component type of an array to still use the exportable name, so recurse if array
			if (type.isLocalClass() || type.isAnonymousClass())
			{
				throw new PersistenceException("Local and anonymous classes cannot be persisted: " + type); //$NON-NLS-1$
			}
			if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers()))
			{
				throw new PersistenceException("Non-static member classes cannot be persisted: " + type); //$NON-NLS-1$
			}
			if (type.isArray())
			{
				name = getNameFromType(type.getComponentType()) + "[]"; //$NON-NLS-1$
			}
			else
			{
				name = type.getName().replace('$', '-');

			}
		}
		if (Util.isEmpty(name))
		{
			throw new PersistenceException("Could not determine name for type: " + type); //$NON-NLS-1$
		}
		return name;
	}

	/**
	 * Throws an {@link IllegalArgumentException} if the given type is not
	 * {@link Exportable} (or doesn't have a default constructor).
	 * 
	 * @param type
	 *            Type to test
	 * @throws PersistenceException
	 */
	protected void assertIsExportable(Class<?> type) throws PersistenceException
	{
		if (getAdapter(type, null) != null)
		{
			return;
		}
		if (AnnotationUtil.getAnnotation(type, Exportable.class) == null)
		{
			throw new PersistenceException(type
					+ " is not marked " + Exportable.class.getSimpleName() + " and has no registered adapter."); //$NON-NLS-1$ //$NON-NLS-2$
		}
		try
		{
			type.getDeclaredConstructor();
		}
		catch (NoSuchMethodException e)
		{
			throw new PersistenceException(type + " does not have a default constructor"); //$NON-NLS-1$
		}
	}

	/**
	 * Throws an {@link IllegalArgumentException} if the given type is not
	 * {@link StringInstantiable#isInstantiable(Class)}.
	 * 
	 * @param type
	 *            Type to test
	 * @throws PersistenceException
	 */
	protected void assertIsStringInstantiable(Class<?> type) throws PersistenceException
	{
		if (!StringInstantiable.isInstantiable(type))
		{
			throw new PersistenceException("Cannot persist type: " + type); //$NON-NLS-1$
		}
	}

	/**
	 * Helper class used to map primitive and boxed classes to simple names, and
	 * vice-versa.
	 */
	protected static class PrimitiveNames
	{
		public static final Map<Class<?>, String> PRIMITIVE_TO_NAME;
		public static final Map<String, Class<?>> NAME_TO_PRIMITIVE;

		static
		{
			Map<Class<?>, String> ptn = new HashMap<Class<?>, String>();
			Map<String, Class<?>> ntp = new HashMap<String, Class<?>>();
			add(int.class, Integer.class, ptn, ntp);
			add(short.class, Short.class, ptn, ntp);
			add(long.class, Long.class, ptn, ntp);
			add(char.class, Character.class, ptn, ntp);
			add(byte.class, Byte.class, ptn, ntp);
			add(float.class, Float.class, ptn, ntp);
			add(double.class, Double.class, ptn, ntp);
			add(boolean.class, Boolean.class, ptn, ntp);
			PRIMITIVE_TO_NAME = Collections.unmodifiableMap(ptn);
			NAME_TO_PRIMITIVE = Collections.unmodifiableMap(ntp);
		}

		private static void add(Class<?> primitive, Class<?> boxed, Map<Class<?>, String> primitiveToName,
				Map<String, Class<?>> nameToPrimitive)
		{
			String name = primitive.getCanonicalName();
			primitiveToName.put(primitive, name);
			primitiveToName.put(boxed, name);
			nameToPrimitive.put(name, boxed);
		}
	}

	/**
	 * Internally used exception that is thrown when an expected
	 * {@link Persistent} element is missing.
	 */
	protected static class MissingPersistentException extends PersistenceException
	{
		public MissingPersistentException(String message)
		{
			super(message);
		}
	}
}
