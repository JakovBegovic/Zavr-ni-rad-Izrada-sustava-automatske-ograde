#pragma once
#include <Arduino.h>
#include <WString.h>


#include "WS2812BControl.h"
WS2812BControl ws2812BControl;

#include "ElectronicsControl.h"

// Izvor: https://www.hackster.io/botletics/esp32-ble-android-arduino-ide-awesome-81c67d

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID           "c5374e85-a035-4d2b-a41a-104c3c8198a2"
#define CHRCTRSTC_WRITE_UUID   "c5374e86-a035-4d2b-a41a-104c3c8198a2"
#define CHRCTRSTC_READ_UUID    "c5374e87-a035-4d2b-a41a-104c3c8198a2"

BLEServer* bleServer;
BLEService* bleService;
BLECharacteristic* bleReadCharacteristic;
BLECharacteristic* bleWriteCharacteristic;

#include "SDCommunicator.h"
SDCommunicator sdCommunicator;
const char* fileName = "/log.txt";

const int readStringsLength = 11;
String readStrings[ readStringsLength ];
int readIndex = 0;

int8_t currentValue = 0;
bool deviceConnected = false;

class MyServerCallbacks: public BLEServerCallbacks
{
    void onConnect( BLEServer* server )
    {
        Serial.println( "Device connected" );
        deviceConnected = true;
    };

    void onDisconnect( BLEServer* server )
    {
        Serial.println( "Device disconnected" );
        deviceConnected = false;

        ws2812BControl.setColor( CRGB::Black );
        electronicsControl.defaultState();
    }
};

class MyWriteCallback: public BLECharacteristicCallbacks
{
    void onWrite( BLECharacteristic* pCharacteristic, esp_ble_gatts_cb_param_t* param )
    {
        std::string value = pCharacteristic->getValue();

        if ( value.length() > 0 )
        {
            int8_t intValue = value[ 0 ]; // get the first byte

            if ( intValue >= -100
                 && intValue <= 100 )
            {

                if( !irAllowsSignal )
                {
                    intValue = 0;
                }
                else if( intValue > 0 && !switch1AllowsSignal )
                {
                    intValue = 0;
                }
                else if( intValue < 0 && !switch2AllowsSignal )
                {
                    intValue = 0;
                }

                electronicsControl.setSpeedNDir( intValue );

                if( currentValue > 0 )
                {
                    ws2812BControl.setColor( CRGB::Red, int( intValue / 3 ) );
                }
                else if( currentValue < 0 )
                {
                    ws2812BControl.setColor( CRGB::Green, int( -1 * intValue / 3 ) );
                }
                else
                {
                    ws2812BControl.setColor( CRGB::Black );
                }
            }



            else
            {

                Serial.println( "Value out of range [-100, 100]" );
            }
        }

    }

};

class MyReadCallback: public BLECharacteristicCallbacks
{
    void onRead( BLECharacteristic* pCharacteristic )
    {
        Serial.println( "Read" );

        if( readIndex == 0 )
        {
            // Get data from SD card
            sdCommunicator.readLastNlines( fileName, readStrings, readStringsLength - 1 );

            readStrings[ readStringsLength - 1 ] = "1010101010";

            Serial.println( "readStrings: " );

            for( int i = 0; i < readStringsLength; i++ )
            {
                Serial.println( String( readStrings[ readStringsLength ] ) );
            }
        }

        bleReadCharacteristic->setValue( readStrings[ readIndex ].c_str() );
        readIndex = ( readIndex + 1 ) % readStringsLength;
        Serial.println( "readIndex " + String( readIndex ) );

    }
};

void setupBLE()
{
// Create the BLE Device
    BLEDevice::init( "Gate-controller" ); // Give it a name

    // Create the BLE Server
    bleServer = BLEDevice::createServer();
    bleServer->setCallbacks( new MyServerCallbacks() );

    // Create the BLE Service
    bleService = bleServer->createService( SERVICE_UUID );

    // Create a read characteristic
    bleReadCharacteristic = bleService->createCharacteristic(
                                CHRCTRSTC_READ_UUID,
                                BLECharacteristic::PROPERTY_READ );
    bleReadCharacteristic->setCallbacks( new MyReadCallback() );

    // Create a write characteristic
    bleWriteCharacteristic = bleService->createCharacteristic(
                                 CHRCTRSTC_WRITE_UUID,
                                 BLECharacteristic::PROPERTY_WRITE );

    bleWriteCharacteristic->setCallbacks( new MyWriteCallback() );

    // Start the service
    bleService->start();

    // Start advertising
    bleServer->getAdvertising()->start();
    Serial.println( "Waiting a client connection to notify..." );
}