# QZA stats proxy

This is how QZA is able to get player stats.

It needs no API key. Player names are resolved through playerdb with minetools
and Mojang as fallbacks, and the stats themselves come from soopy.dev. Nothing
has to be registered, stored or rotated, so there is no key to be revoked.

Deploy it with `npx wrangler deploy` from this folder.

Magical power is the highest the player has reached, which is the figure the
keyless source exposes.
