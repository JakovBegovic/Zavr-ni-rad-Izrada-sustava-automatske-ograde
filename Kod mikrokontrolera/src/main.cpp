#include "BLEController.h"

void setup()
{
    Serial.begin( 115200 );

    setupBLE();

    if( !sdCommunicator.setupSD() )
    {
        return;
    }

    sdCommunicator.printAllFileContents( fileName );


    ws2812BControl.setColor( CRGB::Black );

    electronicsControl.defaultState();

    pinMode( irInterruptPIN, INPUT_PULLUP );
    attachInterrupt( digitalPinToInterrupt( irInterruptPIN ), irInterrupt, CHANGE );

    pinMode( switch1InterruptPIN, INPUT_PULLDOWN );
    attachInterrupt( digitalPinToInterrupt( switch1InterruptPIN ), switch1Interrupt, CHANGE );

    pinMode( switch2InterruptPIN, INPUT_PULLUP );
    attachInterrupt( digitalPinToInterrupt( switch2InterruptPIN ), switch2Interrupt, CHANGE );
}

void loop()
{
    /*

    if( !irAllowsSignal )
    {
        Serial.println( "irAllowsSignal false" );
    }

    if( !switch1AllowsSignal )
    {
        Serial.println( "switch1AllowsSignal false" );
    }

    if( !switch2AllowsSignal )
    {
        Serial.println( "switch2AllowsSignal false" );
    }

    */

    if( deviceConnected )
    {
        Serial.println( electronicsControl.getLastSpeed() );
    }

    delay( 500 );
}