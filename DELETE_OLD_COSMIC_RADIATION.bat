@echo off
rem Run from the project root (spellmdk) AFTER unzipping. Removes the OLD Cosmic Radiation (replaced by Garou's new one).
rmdir /s /q "src\main\java\com\example\jjkspells\cosmic"
del "src\main\java\com\example\jjkspells\spells\CosmicRadiationManager.java"
del "src\main\java\com\example\jjkspells\spells\CosmicRadiationSpell.java"
del "src\main\resources\assets\jjk_spells\particles\cs_*.json"
del "src\main\resources\assets\jjk_spells\textures\particle\cs_*.png"
del "src\main\resources\assets\jjk_spells\textures\mob_effect\cosmic_radiation.png"
del "src\main\resources\assets\jjk_spells\textures\gui\spell_icons\cosmic_radiation.png"
