#pragma once
#include <SD.h>
#include <Arduino.h>
#include <SPI.h>

class SDCommunicator
{
public:
    bool setupSD()
    {

        if ( !SD.begin( 5 ) )
        {
            Serial.println( "SD begin failed!" );
            return 0;
        }

        Serial.println( "SD begun" );

        return 1;
    }

    void clearContents( const char *fileName )
    {
        File fileWrite = SD.open( fileName, FILE_WRITE );
        fileWrite.print( "" );
        fileWrite.close();
    }

    String readAll( const char *fileName )
    {
        String data = "";

        File fileRead = SD.open( fileName, FILE_READ );
        data.concat( fileRead.readString() );
        fileRead.close();

        return data;
    }

    void printAllFileContents( const char *fileName )
    {
        Serial.println( "ALL FILE CONTENTS:" );
        Serial.print( readAll( fileName ) );
        Serial.println( "|------------------------------------" );
    }

    void appendToFile( const char *fileName, String line )
    {
        String data = readAll( fileName );
        data += line;

        File dataFile = SD.open( fileName, FILE_WRITE );

        if ( dataFile )
        {
            dataFile.print( data );
            dataFile.close();
            Serial.println( "Data appended." );
        }
        else
        {
            Serial.println( "Error opening file for appending." );
        }
    }

    void readLastNlines( const char *fileName, String readStrings[], int nOfLines )
    {
        File fileRead = SD.open( fileName, FILE_READ );

        if ( !fileRead )
        {
            Serial.println( "Failed to open log.txt" );
            return;
        }

        // Move the file pointer to the end of the file
        fileRead.seek( 0, SeekEnd );


        int newlineCount = 0;
        String line = "";

        while ( fileRead.position() > 0 && newlineCount < nOfLines )
        {
            fileRead.seek( fileRead.position() - 2 );

            char c = fileRead.read();

            if ( c != '\n' )
            {
                line = String( c ) + line;
            }
            else
            {
                // We are reading the values backwards, so they must be appended in the reverse order
                Serial.println( line );
                readStrings[ nOfLines - newlineCount - 1 ] = line;

                newlineCount++;
                line = "";
            }
        }

        fileRead.close();
    }

    String* readLastNlines( const char *fileName, int nOfLines )
    {
        String* readStrings = new String[ nOfLines ];
        File fileRead = SD.open( fileName, FILE_READ );

        if ( !fileRead )
        {
            Serial.println( "Failed to open log.txt" );
            return readStrings;
        }

        // Move the file pointer to the end of the file
        fileRead.seek( 0, SeekEnd );


        int newlineCount = 0;
        String line = "";

        while ( fileRead.position() > 0 && newlineCount < nOfLines )
        {
            // Move the cursor 1 byte backward
            fileRead.seek( fileRead.position() - 2 );

            char c = fileRead.read();

            if ( c != '\n' )
            {
                line = String( c ) + line;
            }
            else
            {
                // We are reading the values backwards, so they must be appended in the reverse order
                Serial.println( line );
                readStrings[ nOfLines - 1 - newlineCount ] = line;

                newlineCount++;
                line = "";
            }
        }

        fileRead.close();

        return readStrings;
    }
};
