./gradlew zipDebug
adb push ./app/build/distributions/debug/* /storage/emulated/0/Download
echo "b103340f0e86f1d48f44ff38e266a33f0dd12116" > ./token.txt
adb push ./token.txt /storage/emulated/0/Download
rm ./token.txt
