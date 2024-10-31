#pragma once
#include <Arduino.h>
#include <stdlib.h>

// Interrupts
volatile bool irAllowsSignal = true;
volatile bool switch1AllowsSignal = true;
volatile bool switch2AllowsSignal = true;

const int irInterruptPIN = 12;
const int switch1InterruptPIN = 36;
const int switch2InterruptPIN = 14;

class ElectronicsControl
{
private:
    int8_t lastSpeed = 0;
    const int dirTransistorPIN = 27;
    const int speedMosfetPIN = 25;

    const int pwmMosfetChannel = 0;
    const int pwmFrequency = 100; // in Hz
    const int pwmResolution = 8; // in bits (8 bits = 0-255)

public:
    ElectronicsControl()
    {
        pinMode( dirTransistorPIN, OUTPUT );

        ledcSetup( pwmMosfetChannel, pwmFrequency, pwmResolution );
        ledcAttachPin( speedMosfetPIN, pwmMosfetChannel );
    }

    void setSpeedNDir( int8_t speedValue )
    {
        if( !signsAreSame( lastSpeed, speedValue ) )
        {
            changeDir( speedValue >= 0 );
        }

        setSpeed( speedValue );
    }

    void setSpeed( int8_t speedValue )
    {
        ledcWrite( pwmMosfetChannel, valToDuty( speedValue ) );
        lastSpeed = speedValue;
    }

    void stopMotor()
    {
        ledcWrite( pwmMosfetChannel, 0 );
        lastSpeed = 0;
    }

    void defaultState()
    {
        ledcWrite( pwmMosfetChannel, 0 );
        lastSpeed = 0;

        digitalWrite( dirTransistorPIN, LOW );
    }

    bool signsAreSame( int num1, int num2 )
    {
        return ( num1 >= 0 ) == ( num2 >= 0 );
    }

    void changeDir( bool speedValueIsPositive )
    {
        // Stop electricity flow
        ledcWrite( pwmMosfetChannel, 0 );

        delay( 5 );

        if( speedValueIsPositive )
        {
            digitalWrite( dirTransistorPIN, LOW );
        }
        else
        {
            digitalWrite( dirTransistorPIN, HIGH );
        }

        delay( 5 );
    }

    uint8_t valToDuty( int speedValue )
    {
        return int( 2.551 * abs( speedValue ) );
    }

    int8_t getLastSpeed()
    {
        return lastSpeed;
    }
};

ElectronicsControl electronicsControl;

void IRAM_ATTR irInterrupt()
{
    irAllowsSignal = ( digitalRead( irInterruptPIN ) == LOW );

    if( !irAllowsSignal )
    {
        electronicsControl.stopMotor();
    }
}

void IRAM_ATTR switch1Interrupt()
{
    switch1AllowsSignal = ( digitalRead( switch1InterruptPIN ) == LOW );

    if( electronicsControl.getLastSpeed() > 0 && !switch1AllowsSignal )
    {
        electronicsControl.stopMotor();
    }
}

void IRAM_ATTR switch2Interrupt()
{
    switch2AllowsSignal = ( digitalRead( switch2InterruptPIN ) == HIGH );

    if( electronicsControl.getLastSpeed() < 0 && !switch2AllowsSignal )
    {
        electronicsControl.stopMotor();
    }
}