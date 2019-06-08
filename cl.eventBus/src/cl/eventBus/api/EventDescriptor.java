package cl.eventBus.api;

import java.util.Objects;

/**
 * The class <b>EventDescriptor</b> allows to.<br>
 */
public class EventDescriptor<E>
{
  final String topic;
  final Class<E> clazz;

  public EventDescriptor(String topic, Class<E> clazz)
  {
    Objects.requireNonNull(topic);
    Objects.requireNonNull(clazz);

    this.topic = topic;
    this.clazz = clazz;
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(clazz, topic);
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    EventDescriptor<?> other = (EventDescriptor<?>) obj;
    return Objects.equals(clazz, other.clazz) && Objects.equals(topic, other.topic);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[topic='" + topic + "', class='" + clazz.getName() + "']";
  }

  public final String getTopic()
  {
    return topic;
  }

  public final Class<E> getEventDescriptorClass()
  {
    return clazz;
  }
}
