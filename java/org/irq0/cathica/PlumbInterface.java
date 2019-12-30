package org.irq0.cathica;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.DBusInterface;

public interface PlumbInterface extends DBusInterface
{
  public void clipboard();
  public void clipboard_default_action();
  public void string(String str);
  public void string_default_action(String str);
}
