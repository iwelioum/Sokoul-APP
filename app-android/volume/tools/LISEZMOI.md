# Préparer la box Google TV (une seule fois, par adb)

Sur Android TV, le système garde Volume + / − pour lui et les envoie à la TV par HDMI-CEC (que les TCL non Android
ignorent). Pour que Volume TCL puisse les relayer, la box doit régler **son propre** volume :

```sh
# 1. ne plus envoyer le volume par HDMI-CEC
adb shell cmd hdmi_control cec_setting set volume_control_enabled 0
# 2. sortie HDMI en volume variable (0) au lieu de « volume fixe » (1)
javac --release 11 -cp $ANDROID_SDK/platforms/android-35/android.jar -d out VolBehavior.java
d8 --min-api 24 --output . out/VolBehavior.class
adb push classes.dex /data/local/tmp/volb.dex
adb shell CLASSPATH=/data/local/tmp/volb.dex app_process / VolBehavior 0
```

Revenir en arrière : `cec_setting set volume_control_enabled 1` et `VolBehavior 1`.
Les deux réglages survivent au redémarrage de la box.
