package cl.eventBus.api;

import java.util.Collection;

/**
 * The class <b>EventException</b> allows to.<br>
 */
public class EventException extends RuntimeException
{
  private static final long serialVersionUID = 2046156630099135695L;

  /**
   * Constructor
   */
  public EventException(Collection<? extends Exception> eventExceptions)
  {
    eventExceptions.forEach(this::addSuppressed);
  }
}
