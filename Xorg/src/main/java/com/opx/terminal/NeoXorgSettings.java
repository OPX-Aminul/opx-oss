package com.opx.terminal;

import com.opx.terminal.xorg.NeoXorgViewClient;

public class NeoXorgSettings {
  public static void init(NeoXorgViewClient client) {
    Settings.Load(client);
  }
}
