# Using addons

Open the game's mod list, pick **Alterra**, press **Config**, and choose **Addons** in the sidebar.

* **Listed** - the official addons, plus any added to [`addons.json`](../addons.json) by pull request.
* **Community** - repositories on GitHub that carry the topic `alterra-addon`. Nobody has checked these.

Each row shows the addon, its version, and a button: **Install**, **Update** (a newer version is listed), **Remove**.
Installing downloads the jar into your `mods` folder after checking it against the SHA-256 in the list. The game tells you when a restart
is needed; nothing changes until then. An addon that needs another mod (Create, Farmer's Delight...) cannot be installed until that mod
is there, and says so.

If the list cannot be loaded ("HTTP 404", "timed out"), the computer is offline or the list is not published; **Refresh** tries again.
Alterra never downloads anything on its own: only when you press a button.
