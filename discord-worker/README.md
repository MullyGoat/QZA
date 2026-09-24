# QZA Discord relay

Pings a player on Discord when their dungeon party fills up. Link game to their Discord account once with a
code from doing "/qza discord link" in minecraft.

Three steps:

1. In game, `/qza discord link` (or the **Link** button under
   **Notifications → Discord** in `/qza`). It prints a six character code.
2. In the QZA Discord, run `/link <code>`. Only you see the reply.
3. Turn on **Party Full Alert** in `/qza`, and check it with
   `/qza discord test`.

`/unlink` in Discord stops the alerts and deletes everything stored about you.
So does `/qza discord unlink` in game.
