# Protocol

So, we have a means of sending bytes across bluetooth. What I want is a means to send arbitrarily complex things across bluetooth.

So here's what I am thinking.

I'll send a nested structure.

An item may be a primitive or an object.
An object may be a map or an array.
Object keys are bytes - an object may have no more that 256 keys, which should be plenty. Values may be any type.
Arrays have all thier elements with the same types.
Streams and blobs are not dealt with - you'll have to impose a higher-level protocol.
Primitives are 1,2,4,8 byte ints and strings.

Code will read these things as streams. This means that the callback gets a stack as an array:

* this is the base item. It's an object
* this is key 4 of the object. It's an array of object with 10 items.
* This is element 9 of the array. It's an object (obviously).
* this is key 2 of the object. It's a 2-byte int. Its value is 3.

I'd like the wire protocol to be readble on the serial monitor. Chunks will be base-64 encoded variable-length, followed by a checksum in hex. Chunks will be delimited with a leading [ and a trailing ]. White space is ignored. Bad chunks get discarded, so the app needs to be written so that it can cope with dropped packets. 

Actually - forget the base-64 encoding. I'll just use something readable.

## Types:

### primitives -
* 'n' - NULL
* 'b' - byte. hex value
* 'i' - 16-bit. then hex value
* 'l' - 32-bit. then hex value
* 'x' - 64 bit. then hex value
* 's' - string. base64 encoded string. Perhaps not implemented right now. terminated with '===' if the length is on the boudary.

### Array
'[', then type, then values without the type byte, then ']'

### Object
'{', then a sequence of key (two-digit hex), then object

## Packet

'<', then one item, then '#', then checksum in hexadecimal, then '>'.

## example

    <{01bBE02i000105[szzzz==xxx======]}nlFEElDEAD#DEADBEEF>
    
In JSON:

    {
      1: 0xBE,
      2: 0x0001,
      5: [ "???", "??", ""]
    }
    
With a 0xDEADBEEF checksum.
    
The incoming stream reader gets this sequence of stuff 

* start stream. a stream is always an array
* stream element 0 - object
* stream element 0 - object, key 1 - byte 0xBE
* stream element 0 - object, key 2 - int 0x0001
* stream element 0 - object, key 5 - array of string
* stream element 0 - object, key 5 - array of string, element 0 "???"
* stream element 0 - object, key 5 - array of string, element 1 "??"
* stream element 0 - object, key 5 - array of string, element 2 ""
* stream element 0 - object, key 5 - array of string, END
* stream element 0 - object, END
* stream element 1 - null
* stream element 2 - 32-bit value 0xFEE1DEAD
* checksum is ok, good to go.

# Well, that's the plan, anyway.
