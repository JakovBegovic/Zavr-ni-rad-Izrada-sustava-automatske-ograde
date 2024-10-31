#pragma once
#include <FastLED.h>

class WS2812BControl
{
private:
    CRGB leds[ 1 ];
public:


    WS2812BControl()
    {
        FastLED.addLeds< WS2812B, 32, RGB >( leds, 1 );
    }

    void setColor( CRGB color )
    {
        leds[ 0 ] = color;

        FastLED.show();
    }

    void setColor( CRGB color, uint8_t intensity )
    {

        FastLED.setBrightness( intensity );

        setColor( color );
    }



};