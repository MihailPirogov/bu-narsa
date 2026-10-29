#!/bin/bash
export JAVA_HOME=/home/box/tools/jdk-17.0.20.1+1; export PATH=$JAVA_HOME/bin:/home/box/tools/gradle-8.13/bin:$PATH; export ANDROID_HOME=/home/box/tools/android-sdk
cd /workspace/tatar-kupu
for c in 480x854_240dpi land_2400x1080_420dpi font1.3_480x854_240dpi 720x1280_320dpi 1080x2400_420dpi 1440x3200_560dpi tablet_1600x2560_320dpi font1.3_1080x2400_420dpi; do
  if ls tests/screens/${c}__6_result_long.png >/dev/null 2>&1; then continue; fi
  timeout 900 /home/box/tools/gradle-8.13/bin/gradle :app:recordRoborazziDebug --tests '*ScreensTest.screens' -Pcfg=$c -q > /tmp/robo_$c.log 2>&1
  echo "$c exit=$? $(ls tests/screens/${c}__* 2>/dev/null | wc -l) png" | tee -a tests/report.md
done
echo ALLDONE
