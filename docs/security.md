# Security

An addon is a mod jar and runs with the game's permissions, so the manager is careful:

- it only downloads github.com links ending in .jar, and for community addons the jar has to be a release of the repo that lists it
- every download is checked against the SHA-256 in the list, and thrown away if it doesn't match
- there's a 64 MB limit, ids and versions are validated, and control characters are stripped from text it shows
- it only downloads or deletes when you press a button, nothing in the background, and nothing loads until you restart
- community addons are marked as not reviewed and it asks a second time

It can't tell you if a jar is harmless. Listed addons get looked at (the addon and its source) before they're added, and pull requests are
checked automatically. For community addons, look at the repo first.

If you find a problem with an addon or the manager, open an issue. For something serious use "Report a vulnerability" on this repo.
