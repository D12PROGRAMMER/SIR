#!/usr/bin/env python3
"""Qt6 test application for ui-mcp. Mirrors the GTK app:
Save button (objectName qt-save), line edit (qt-filename)."""
import sys

from PyQt6.QtWidgets import (
    QApplication, QLineEdit, QMainWindow, QPushButton, QVBoxLayout, QWidget,
)

app = QApplication(sys.argv)
app.setApplicationName("qt-test-app")

win = QMainWindow()
win.setWindowTitle("Qt Test App")
central = QWidget()
layout = QVBoxLayout(central)

entry = QLineEdit()
entry.setObjectName("qt-filename")
entry.setAccessibleName("Filename")
layout.addWidget(entry)


def on_save():
    with open("/tmp/qt-save-pressed", "w") as f:
        f.write(f"filename={entry.text()}\n")
    win.setWindowTitle("Qt Saved")
    print("QT SAVED", flush=True)


btn = QPushButton("Save")
btn.setObjectName("qt-save")
btn.setAccessibleName("Save")
btn.clicked.connect(on_save)
layout.addWidget(btn)

win.setCentralWidget(central)
win.show()
sys.exit(app.exec())
