BASEDIR=$(dirname $0)
PRODUCT_PATH=$1
echo $PRODUCT_PATH
mkdir -p $PRODUCT_PATH/system/app/TerminalEmulator/lib/arm/
cp -a $BASEDIR/term/lib/arm/* $PRODUCT_PATH/system/app/TerminalEmulator/lib/arm/
cp $BASEDIR/term/build/outputs/apk/term-release-unsigned.apk $BASEDIR/TerminalEmulator.apk
