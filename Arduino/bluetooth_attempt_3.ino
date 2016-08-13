#include <SoftwareSerial.h>

class SimpleChecksum {
  public:
    uint32_t checksumComputed;

    void clear() {
      checksumComputed = 0xDEBB1E;
    }

    void add(char c) {
      checksumComputed = ((checksumComputed << 19) ^ (checksumComputed >> 5) ^ c) & 0xFFFFFF;
    }
};

class BtReader {
  public:
    Stream &in;
    uint32_t mostRecentHeartbeatMs;

    enum BtStreamState {
      BT_START = 0,
      BT_GOT_0 = 1,
      BT_GOT_1 = 2,
      BT_GOT_2 = 3,
      BT_GOT_3 = 4,
      BT_CHECKSUM = 5,
      BT_ERROR = 6
    } state;


    class Callback {
      public:
        virtual void transition(BtReader::BtStreamState from, BtReader::BtStreamState to) {}
        virtual void gotBytes(byte *buf, int ct) = 0;
        virtual void bufferExceeded(byte *buf, int ct) {}
        virtual void checksumMismatch(byte *buf, int ct, uint32_t expected, uint32_t received)  {}
    } &callback;


    byte base64Chunk[5];

    static const int BUFLEN = 100;

    // the chunk decoder overflows, so we need to make room
    byte buffer[BUFLEN + 4];
    int bufCt;

    SimpleChecksum checksum;

    uint32_t checksumRead;

    BtReader(Stream &in, Callback &callback) : in(in), callback(callback) {
      transition_to_start();
    }

    void setup() {
      mostRecentHeartbeatMs = millis();
    }

    void loop() {
      while (in.available() > 0) {

        int ch = in.read();

        if (ch == -1) return;
        if (ch <= ' ') return; // ignore white space

        // asterisks are a heartbeat signal
        if (ch == '*') {
          mostRecentHeartbeatMs = millis();
          return;
        }

        switch (state) {
          case BT_START:
            if (ch == '<') {
              transitionTo(BT_GOT_0);
            }
            break;

          case BT_GOT_0:
            if (isBase64(ch)) {
              base64Chunk[0] = ch;
              transitionTo(BT_GOT_1);
            }
            else if (ch == '#') {
              checksumRead = 0;
              transitionTo(BT_CHECKSUM);
            }
            else if (ch == '<') {
              // ignore multiple start-angle.
            }
            else {
              transitionTo(BT_ERROR);
            }
            break;

          case BT_GOT_1:
            if (isBase64(ch)) {
              base64Chunk[1] = ch;
              transitionTo(BT_GOT_2);
            }
            else {
              transitionTo(BT_ERROR);
            }
            break;

          case BT_GOT_2:
            if (isBase64(ch)) {
              base64Chunk[2] = ch;
              transitionTo(BT_GOT_3);
            }
            else {
              transitionTo(BT_ERROR);
            }
            break;

          case BT_GOT_3:
            if (isBase64(ch)) {
              base64Chunk[3] = ch;

              if (handleValidChunk()) {
                transitionTo(BT_GOT_0);
              }
              else {
                transitionTo(BT_ERROR);
              }
            }
            else
              transitionTo(BT_ERROR);
            break;

          case BT_CHECKSUM:
            if (ch >= '0' && ch <= '7') {
              checksumRead = checksumRead << 3 | (ch - '0');
            }
            else if (ch == '>') {
              buffer[bufCt] = 0;

              if (checksum.checksumComputed != checksumRead) {
                callback.checksumMismatch(buffer, bufCt, checksum.checksumComputed, checksumRead);
              }
              else {
                callback.gotBytes(buffer, bufCt);
              }
              transition_to_start();
            }
            else {
              transitionTo(BT_ERROR);
            }
            break;

          case BT_ERROR:
            if (ch == '>') transition_to_start();
            // else stay in error
            break;
        }
      }
    }


    void transition_to_start() {
      bufCt = 0;
      checksum.clear();
      checksumRead = 0;
      transitionTo(BT_START);
    }

    void transitionTo(BtStreamState newstate) {
      BtStreamState stateWas = state;
      state = newstate;
      callback.transition(stateWas, state);

    }

    boolean isBase64(byte ch) {
      return
        (ch >= 'A' && ch <= 'Z') ||
        (ch >= 'a' && ch <= 'z') ||
        (ch >= '0' && ch <= '9') ||
        (ch == '+') || (ch == '/') || (ch == '=');
    }

    byte sixBit(byte c) {
      if (c >= 'A' && c <= 'Z') return c - 'A';
      if (c >= 'a' && c <= 'z') return c - 'a' + 26;
      if (c >= '0' && c <= '9') return c - '0' + 52;
      if (c == '+') return  62;
      if (c == '/') return 63;
      return 0;
    }

    boolean handleValidChunk() {
      if (base64Chunk[0] == '=' && base64Chunk[1] != '=') return false;
      if (base64Chunk[1] == '=' && base64Chunk[2] != '=') return false;
      if (base64Chunk[2] == '=' && base64Chunk[3] != '=') return false;

      if (base64Chunk[0] == '=') return true; // ok chunk, empty
      if (base64Chunk[1] == '=') return false; // bad chunk

      // staging area.
      // this stuff relies on sixbit returning zero for '='

      uint32_t stage =
        ((uint32_t)sixBit(base64Chunk[0]) << 18) |
        ((uint32_t)sixBit(base64Chunk[1]) << 12) |
        ((uint32_t)sixBit(base64Chunk[2]) << 6) |
        ((uint32_t)sixBit(base64Chunk[3]) << 0);

      if (bufCt >= BUFLEN) {
        buffer[BUFLEN] = 0;
        callback.bufferExceeded(buffer, bufCt);
        return false;
      }

      checksum.add(buffer[bufCt++] = (stage >> 16));

      if (base64Chunk[2] == '=') return true;

      if (bufCt >= BUFLEN) {
        buffer[BUFLEN] = 0;
        callback.bufferExceeded(buffer, bufCt);
        return false;
      }

      checksum.add(buffer[bufCt++] = (stage >> 8));

      if (base64Chunk[3] == '=') return true;

      if (bufCt >= BUFLEN) {
        buffer[BUFLEN] = 0;
        callback.bufferExceeded(buffer, bufCt);
        return false;
      }

      checksum.add(buffer[bufCt++] = (stage >> 0));

      return true;
    }
};

// TODO
// class BtReaderStream : public Stream

class BtWriter {
  public:
    SoftwareSerial &out;
    uint32_t mostRecentHeartbeatMs;
    SimpleChecksum checksum;

    char buf[513]; // half the max bluetoth packet size.
    int bufCt = 0;

    BtWriter(SoftwareSerial &out) : out(out) {}

    void setup() {
      mostRecentHeartbeatMs = millis();
    }

    void loop() {
      if (millis() - mostRecentHeartbeatMs > 10000) {
        mostRecentHeartbeatMs = millis();
        out.write('*');
      }
    }

    void write(char *bytes, int offs, int len) {
      flush();
      checksum.clear();
      putCh('<');

      bytes += offs;
      while (len > 0) {
        if (len >= 3) {
          put3(bytes);
          bytes += 3;
          len -= 3;
        }
        else if (len == 2)  {
          put2(bytes);
          bytes += 2;
          len -= 2;
        }
        else if (len == 1)  {
          put1(bytes);
          bytes++;
          len --;
        }
      }
      
      putCh('#');

      for (int i = 21; i >= 0; i -= 3) {
        putCh('0' + ((checksum.checksumComputed >> i) & 7));
      }

      putCh('>');

      flush();
    }

    void put3(char *bytes) {
      checksum.add(bytes[0]);
      checksum.add(bytes[1]);
      checksum.add(bytes[2]);

      uint32_t stage = (((uint32_t)bytes[0]) << 16) | (((uint32_t)bytes[1]) << 8) | (((uint32_t)bytes[2]) << 0);

      putCh(to64((stage >> 18) & 0x3F));
      putCh(to64((stage >> 12) & 0x3F));
      putCh(to64((stage >> 6) & 0x3F));
      putCh(to64((stage >> 0) & 0x3F));
    }

    void put2(char *bytes) {
      checksum.add(bytes[0]);
      checksum.add(bytes[1]);

      uint32_t stage = (((uint32_t)bytes[0]) << 16) | (((uint32_t)bytes[1]) << 8) ;

      putCh(to64((stage >> 18) & 0x3F));
      putCh(to64((stage >> 12) & 0x3F));
      putCh(to64((stage >> 6) & 0x3F));
      putCh('=');
    }

    void put1(char *bytes) {
      checksum.add(*bytes);

      uint32_t stage = (((uint32_t)bytes[0]) << 16) ;

      putCh(to64((stage >> 18) & 0x3F));
      putCh(to64((stage >> 12) & 0x3F));
      putCh('=');
      putCh('=');
    }

    // for completeness
    void put0(char *bytes) {
      putCh('=');
      putCh('=');
      putCh('=');
      putCh('=');
    }

    char to64(char in) {
      if (in < 26) return 'A' + in;
      else if (in < 52) return 'a' + in - 26;
      else if (in < 62) return '0' + in - 52;
      else if (in == 62) return '+';
      else if (in == 62) return '/';
      else return '?';
    }

    void putCh(char c) {
      if (bufCt >= sizeof(buf)) {
        flush();
      }

      buf[bufCt++] = c;
    }

    void flush() {

      // my bluetooth connectin seems to be extremely screwed up, for some reason.
      // the only thing that woks is sending the charactrs one at a time 
      
      if (bufCt > 0) {
        buf[bufCt] = '\0';
        Serial.print("writing to BT: ");
        Serial.println(buf);

        for(int i = 0; i< bufCt; i++) {
          Serial.print(buf[i]);
          out.write(buf[i]);
          delay(100);
        }

        bufCt = 0;
      }
      out.flush();
    }
};

// TODO
// class BtWriterStream : public Stream


class Callback : public BtReader::Callback {
  public:
    void setup() {}
    void loop() {}

    void gotBytes(byte *buf, int ct) {
      buf[ct] = 0;
      Serial.println();
      Serial.print("GOT STRING ");
      Serial.print('<');
      Serial.print((char *) buf);
      Serial.println('>');
    }

    void checksumMismatch(byte *buf, int ct, uint32_t expected, uint32_t received)  {
      buf[ct] = 0;
      Serial.println();
      Serial.print("CHECKSUM MISMATCH ");
      for (int i = 21; i >= 0; i -= 3)
        Serial.print((char)(((expected >> i) & 7) + '0'));
      Serial.print(" =/= ");
      for (int i = 21; i >= 0; i -= 3)
        Serial.print((char)(((received >> i) & 7) + '0'));
      Serial.print(" <");
      Serial.print((char *) buf);
      Serial.println('>');
    }

    void bufferExceeded(byte *buf, int ct) {
      buf[ct] = 0;
      Serial.println();
      Serial.print("BUFFER EXCEEDED ");
      Serial.print('<');
      Serial.print((char *) buf);
      Serial.println('>');
    }


} callback;

// PINOUT

const byte txPin = 9;
const byte rxPin = 8;
SoftwareSerial bt(rxPin, txPin);
BtReader reader(bt, callback);
BtWriter writer(bt);
const byte fooPin = 4;

void setup() {
  // put your setup code here, to run once:

  pinMode(rxPin, INPUT);
  pinMode(txPin, OUTPUT);
  pinMode(fooPin, INPUT_PULLUP);

  Serial.begin(9600);

  bt.begin(9600);

  Serial.print("Beginning in");
  for (int i = 3; i > 0; i--) {
    Serial.print(' ');
    Serial.print(i);
    delay(500);
  }
  Serial.println(" 0");

  bt.print("This is a test message!");

  callback.setup();
  reader.setup();
  writer.setup();
}

void loop() {
  static uint32_t ms;
  static int state;

  // intrestingly, this is a demo of something you might want to do.
  // pin 4 is the 'foo' button

  if (millis() - ms > 200) {
    int stateWas = state;
    state = digitalRead(fooPin);
    if (stateWas == HIGH && state == LOW) {
      writer.write("foo", 0, 3);
      ms = millis();
    }
  }

  reader.loop();
  writer.loop();
  callback.loop();

}

