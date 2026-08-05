Day/night speed
/planetworld time speed <multiplier>   # 1–500× extra dayTime per tick (e.g. 60 for fast sky testing)
/planetworld time speed reset          # back to normal

Season logging & control
/planetworld season log true|false     # chat when northern season quarter changes
/planetworld season query              # current season, progress, warmth
/planetworld season set spring|summer|autumn|winter

- With Serene Seasons: sets SS cycle ticks directly.
- Without SS: jumps dayTime on the 10-day fallback year.

Polar weather
/planetworld polar storm north [seconds]   # default 120s
/planetworld polar storm south [seconds]
/planetworld polar storm both [seconds]
/planetworld polar storm clear

