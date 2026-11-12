# Security

An addon is a mod jar: it runs code with the game's permissions. That is why the in-game manager is careful.

What the manager does:

* Downloads only `https://github.com/...` links ending in `.jar`; a community addon's jar must be a release asset of the repository that lists it.
* Checks every download against the **SHA-256** in the listing before it goes into `mods`; a mismatch is deleted.
* Limits the size (64 MB), validates ids, versions and file names, and strips control characters from text it shows.
* Downloads and removes **only when you press a button**. Nothing happens in the background, and nothing is loaded until you restart.
* Marks anything from the Community tab as *not reviewed* and asks a second time.

What it cannot do: tell you whether a jar is harmless. For the Listed tab, a maintainer reviews the addon and its source before it is
added, and pull requests are checked automatically. For community addons, look at the repository first; install what you trust.

Found a problem with an addon or with the manager? Open an issue, or for something serious use GitHub's "Report a vulnerability" on this repository.
