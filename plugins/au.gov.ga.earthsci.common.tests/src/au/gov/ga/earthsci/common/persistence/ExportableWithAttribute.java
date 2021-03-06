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

@Exportable
public class ExportableWithAttribute
{
	@Persistent(attribute = true)
	private int attribute;

	public int getAttribute()
	{
		return attribute;
	}

	public void setAttribute(int attribute)
	{
		this.attribute = attribute;
	}

	@Override
	public boolean equals(Object obj)
	{
		ExportableWithAttribute ewa = (ExportableWithAttribute) obj;
		return ewa.attribute == attribute;
	}
}
