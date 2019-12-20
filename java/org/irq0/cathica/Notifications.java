package org.irq0.cathica;

import java.util.Map;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusInterfaceName;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.Tuple;

@DBusInterfaceName("org.freedesktop.Notifications")
public interface Notifications extends DBusInterface
{
  String[] GetCapabilities();

  void CloseNotification(UInt32 id);

  Object GetServerInfo();

  Object GetServerInformation();

  UInt32 Notify(String appName, UInt32 id, String icon,
                String summary, String body, String[] actions,
                Map<String, Object> hints, int timeout);
}
