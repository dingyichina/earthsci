package au.gov.ga.earthsci.notification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.eclipse.core.runtime.IStatus;

/**
 * The default implementation of the {@link INotification} interface
 * <p/>
 * Provides a threadsafe implementation that is essentially immutable. Only the
 * acknowledgement indicators may change after object creation, and they can
 * only be changed through a call {@link #acknowledge()}, which is an idempotent
 * operation.
 * 
 * @author James Navin (james.navin@ga.gov.au)
 */
public class Notification implements INotification
{

	private long id;
	private NotificationLevel level;
	private NotificationCategory category;

	private long creationTimestamp;

	private String title;
	private String text;

	private INotificationAction[] actions;

	private boolean requiresAcknowledgement = false;
	private boolean acknowledged = false;
	private INotificationAction acknowledgementAction;
	private long acknowledgementTimestamp = -1;

	private Throwable throwable;

	/**
	 * Create a basic general notification that has no associated actions and
	 * does not require user acknowledgement
	 * 
	 * @param level
	 *            The level of the notification
	 * @param title
	 *            The title for the notification
	 * @param text
	 *            The text to include in the notification
	 */
	public Notification(NotificationLevel level, String title, String text)
	{
		this(level, NotificationCategory.GENERAL, title, text, null, false, null, null);
	}

	/**
	 * Create a notification that has associated actions, but does not require
	 * user acknowledgement
	 * 
	 * @param level
	 *            The level of the notification
	 * @param category
	 *            The category of the notification
	 * @param title
	 *            The title for the notification
	 * @param text
	 *            The text to include in the notification
	 * @param actions
	 *            The list of actions to include in the notification
	 */
	public Notification(NotificationLevel level, NotificationCategory category, String title, String text,
			INotificationAction... actions)
	{
		this(level, category, title, text, actions, false, null, null);
	}

	/**
	 * Create a fully configured notification
	 * 
	 * @param level
	 *            The level of the notification
	 * @param title
	 *            The title for the notification
	 * @param text
	 *            The text to include in the notification
	 * @param actions
	 *            The list of actions to include in the notification
	 * @param requiresAcknowledgement
	 *            Whether or not this notification requires acknowledgement from
	 *            the user
	 * @param acknowledgementAction
	 *            The action to associate with user acknowledgement
	 */
	public Notification(NotificationLevel level, NotificationCategory category, String title, String text,
			INotificationAction[] actions, boolean requiresAcknowledgement, INotificationAction acknowledgementAction,
			Throwable throwable)
	{
		this.level = level;
		this.category = category;
		this.title = title;
		this.text = text;
		this.actions = actions == null ? null : Arrays.copyOf(actions, actions.length);
		this.requiresAcknowledgement = requiresAcknowledgement;
		this.acknowledgementAction = acknowledgementAction;

		this.creationTimestamp = new Date().getTime();

		this.id = UUID.randomUUID().getLeastSignificantBits();

		this.throwable = throwable;
	}

	@Override
	public NotificationLevel getLevel()
	{
		return level;
	}

	@Override
	public NotificationCategory getCategory()
	{
		return category;
	}

	@Override
	public long getId()
	{
		return id;
	}

	@Override
	public String getTitle()
	{
		return title;
	}

	@Override
	public String getText()
	{
		return text;
	}

	@Override
	public INotificationAction[] getActions()
	{
		return actions;
	}

	@Override
	public boolean requiresAcknowledgment()
	{
		return requiresAcknowledgement;
	}

	@Override
	public INotificationAction getAcknowledgementAction()
	{
		return acknowledgementAction;
	}

	@Override
	public synchronized boolean isAcknowledged()
	{
		return acknowledged;
	}

	@Override
	public Date getCreationTimestamp()
	{
		return new Date(creationTimestamp);
	}

	@Override
	public synchronized Date getAcknowledgementTimestamp()
	{
		if (acknowledgementTimestamp < 0)
		{
			return null;
		}
		return new Date(acknowledgementTimestamp);
	}

	@Override
	public synchronized void acknowledge()
	{
		if (isAcknowledged())
		{
			return;
		}

		acknowledged = true;
		acknowledgementTimestamp = new Date().getTime();

		if (acknowledgementAction != null)
		{
			acknowledgementAction.run();
		}
	}

	@Override
	public Throwable getThrowable()
	{
		return throwable;
	}

	/**
	 * Create a new {@link Notification} instance using a builder
	 * 
	 * @param level
	 *            The level of the notification
	 * @param title
	 *            The title for the notification
	 * @param text
	 *            The text to include in the notification
	 */
	public static Builder create(NotificationLevel level, String title, String text)
	{
		return new Builder(level, title, text);
	}

	/**
	 * A builder class for Notification instances that provides a convenient
	 * fluent interface
	 */
	public static class Builder
	{
		private NotificationLevel level;
		private NotificationCategory category;
		private String title;
		private String text;
		private List<INotificationAction> actions = new ArrayList<INotificationAction>();
		private boolean requiresAcknowledgement = false;
		private INotificationAction acknowledgementAction;
		private Throwable throwable;

		/**
		 * @see INotification#getLevel()
		 * @see INotification#getTitle()
		 * @see INotification#getText()
		 */
		public Builder(NotificationLevel level, String title, String text)
		{
			this.level = level;
			this.title = title;
			this.text = text;
		}

		public Builder(IStatus status)
		{
			this.text = status.getMessage();
			this.title = status.getMessage();
			this.level = getLevel(status.getSeverity());
		}

		private NotificationLevel getLevel(int statusSeverity)
		{
			switch (statusSeverity)
			{
			case IStatus.ERROR:
				return NotificationLevel.ERROR;
			case IStatus.WARNING:
				return NotificationLevel.WARNING;
			case IStatus.INFO:
				return NotificationLevel.INFORMATION;
			default:
				return NotificationLevel.INFORMATION;
			}
		}

		/**
		 * @see INotification#getCategory()
		 */
		public Builder inCategory(NotificationCategory category)
		{
			this.category = category;
			return this;
		}

		/**
		 * @see INotification#getActions()
		 */
		public Builder withAction(INotificationAction action)
		{
			if (action == null)
			{
				return this;
			}
			this.actions.add(action);
			return this;
		}

		/**
		 * @see INotification#getActions()
		 */
		public Builder withActions(INotificationAction... actions)
		{
			if (actions == null)
			{
				return this;
			}
			this.actions.addAll(Arrays.asList(actions));
			return this;
		}

		public Builder requiringAcknowledgement(boolean required)
		{
			this.requiresAcknowledgement = required;
			this.acknowledgementAction = required ? new DefaultAcknowledgementAction() : null;
			return this;
		}

		/**
		 * @see INotification#requiresAcknowledgment()
		 * @see INotification#getAcknowledgementAction()
		 */
		public Builder requiringAcknowledgement()
		{
			return requiringAcknowledgement(true);
		}

		/**
		 * @see INotification#requiresAcknowledgment()
		 * @see INotification#getAcknowledgementAction()
		 */
		public Builder requiringAcknowledgement(INotificationAction acknowledgementAction)
		{
			this.requiresAcknowledgement = true;
			this.acknowledgementAction = acknowledgementAction;
			return this;
		}

		/**
		 * @see INotification#getThrowable()
		 */
		public Builder withThrowable(Throwable t)
		{
			this.throwable = t;
			return this;
		}

		public Notification build()
		{
			if (requiresAcknowledgement && acknowledgementAction == null)
			{
				acknowledgementAction = new DefaultAcknowledgementAction();
			}
			return new Notification(level, category, title, text, actions.toArray(new INotificationAction[actions
					.size()]), requiresAcknowledgement, acknowledgementAction, throwable);
		}

	}

	private static class DefaultAcknowledgementAction implements INotificationAction
	{

		@Override
		public String getText()
		{
			return Messages.Notification_DefaultActionText;
		}

		@Override
		public String getTooltip()
		{
			return null;
		}

		@Override
		public void run()
		{
			// NOOP
		}

	}
}
