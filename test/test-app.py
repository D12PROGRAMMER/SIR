#!/usr/bin/env python3
"""GTK3 test application for ui-mcp.
Fixtures:
  save-project  Save button; writes /tmp/save-pressed, retitles window
  filename      text entry
  locked        disabled button
  (2x) Copy     duplicate-name buttons WITHOUT ids -> ambiguity fixture
  spawn         adds a new button (id dynamic-1) at runtime
  despawn       removes the dynamic button
"""
import gi

gi.require_version("Gtk", "3.0")
gi.require_version("Atk", "1.0")
from gi.repository import Gtk, GLib  # noqa: E402

GLib.set_prgname("test-app")


class TestApp(Gtk.Window):
    def __init__(self):
        super().__init__(title="Test App")
        self.set_default_size(340, 260)
        self.presses = 0
        self.dynamic = None

        self.box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        self.add(self.box)

        self.entry = Gtk.Entry()
        self.entry.get_accessible().set_accessible_id("filename")
        self.box.pack_start(self.entry, False, False, 0)

        save = Gtk.Button(label="Save")
        save.get_accessible().set_accessible_id("save-project")
        save.connect("clicked", self.on_save)
        self.box.pack_start(save, False, False, 0)

        locked = Gtk.Button(label="Locked")
        locked.get_accessible().set_accessible_id("locked")
        locked.set_sensitive(False)
        self.box.pack_start(locked, False, False, 0)

        # Ambiguity fixture: two identical buttons, no accessible ids.
        for _ in range(2):
            copy = Gtk.Button(label="Copy")
            copy.connect("clicked", lambda *_: print("COPIED", flush=True))
            self.box.pack_start(copy, False, False, 0)

        spawn = Gtk.Button(label="Add Widget")
        spawn.get_accessible().set_accessible_id("spawn")
        spawn.connect("clicked", self.on_spawn)
        self.box.pack_start(spawn, False, False, 0)

        despawn = Gtk.Button(label="Remove Widget")
        despawn.get_accessible().set_accessible_id("despawn")
        despawn.connect("clicked", self.on_despawn)
        self.box.pack_start(despawn, False, False, 0)

    def on_save(self, _btn):
        self.presses += 1
        with open("/tmp/save-pressed", "w") as f:
            f.write(f"presses={self.presses} filename={self.entry.get_text()}\n")
        self.set_title(f"Saved x{self.presses}")
        print(f"SAVED (x{self.presses})", flush=True)

    def on_spawn(self, _btn):
        if self.dynamic is None:
            self.dynamic = Gtk.Button(label="Dynamic")
            self.dynamic.get_accessible().set_accessible_id("dynamic-1")
            self.dynamic.connect(
                "clicked", lambda *_: print("DYNAMIC PRESSED", flush=True)
            )
            self.box.pack_start(self.dynamic, False, False, 0)
            self.dynamic.show()
            print("SPAWNED dynamic-1", flush=True)

    def on_despawn(self, _btn):
        if self.dynamic is not None:
            self.box.remove(self.dynamic)
            self.dynamic.destroy()
            self.dynamic = None
            print("DESPAWNED dynamic-1", flush=True)


win = TestApp()
win.connect("destroy", Gtk.main_quit)
win.show_all()
Gtk.main()
