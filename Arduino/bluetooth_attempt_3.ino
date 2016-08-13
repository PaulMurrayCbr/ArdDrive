#include <SoftwareSerial.h>

const byte txPin = 9;
const byte rxPin = 8;

SoftwareSerial bt(rxPin, txPin);

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
    uint32_t checksumComputed;
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

              if (checksumComputed != checksumRead) {
                callback.checksumMismatch(buffer, bufCt, checksumComputed, checksumRead);
              }
              else {
                callback.gotBytes(buffer, bufCt);
              }
            }
            else {
              transitionTo(BT_ERROR);
            }
            break;

          case BT_ERROR:
            if (ch == '<') transition_to_start();
            // else stay in error
            break;
        }
      }
    }


    void transition_to_start() {
      bufCt = 0;
      checksumComputed = 0xDEBB1E;
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

      buffer[bufCt++] = (stage >> 16);
      checksumComputed = ((checksumComputed << 19) ^ (checksumComputed >> 5) ^ buffer[bufCt - 1]) & 0xFFFFFF;

      if (base64Chunk[2] == '=') return true;

      if (bufCt >= BUFLEN) {
        buffer[BUFLEN] = 0;
        callback.bufferExceeded(buffer, bufCt);
        return false;
      }

      buffer[bufCt++] = (stage >> 8);
      checksumComputed = ((checksumComputed << 19) ^ (checksumComputed >> 5) ^ buffer[bufCt - 1]) & 0xFFFFFF;

      if (base64Chunk[3] == '=') return true;

      if (bufCt >= BUFLEN) {
        buffer[BUFLEN] = 0;
        callback.bufferExceeded(buffer, bufCt);
        return false;
      }

      buffer[bufCt++] = (stage >> 0);
      checksumComputed = ((checksumComputed << 19) ^ (checksumComputed >> 5) ^ buffer[bufCt - 1]) & 0xFFFFFF;

      return true;
    }
};

// TODO
// class BtReaderStream : public Stream

class BtWriter {
  public:
    SoftwareSerial &out;
    uint32_t mostRecentHeartbeatMs;

    // NOTE!!! Code relies on put/pop wrapping!
    char buf[256];
    uint8_t bufPut = 0;
    uint8_t bufPop = 0;

    BtWriter(SoftwareSerial &out) : out(out) {}

    void setup() {
      mostRecentHeartbeatMs = millis();
    }

    void loop() {
      if (millis() - mostRecentHeartbeatMs > 5000) {
        mostRecentHeartbeatMs = millis();
        bt.write('*');
      }
    }

    void write(char *bytes, int offs, int len) {
      flush();

      putCh('<');
      bytes += offs;
      while(len-- > 0) putCh(*bytes++);
      putCh('#');
      putCh('>');
      
    }

    void putCh(char c) {
      if(((uint8_t)bufPut + 1) == bufPop) {
        flushToSerial();
      }
      buf[bufPut++] = c;
    }

    void flush() {
      flushToSerial();
      out.flush();
    }

    void flushToSerial() {
      while(bufPut != bufPop) out.write(buf[bufPop++]);
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

BtReader reader(bt, callback);
BtWriter writer(bt);

void setup() {
  // put your setup code here, to run once:

  pinMode(rxPin, INPUT);
  pinMode(txPin, OUTPUT);

  Serial.begin(9600);

  bt.begin(9600);

  Serial.print("Beginning in");
  for (int i = 3; i > 0; i--) {
    Serial.print(' ');
    Serial.print(i);
    delay(1000);
  }
  Serial.println(" 0");

  callback.setup();
  reader.setup();
  writer.setup();
}

void loop() {
  // put your main code here, to run repeatedly:

  if (Serial.available()) {
    int ch = Serial.read();
    bt.write((char)ch);
    Serial.print("sending ");
    Serial.println((char)ch);
  }

  reader.loop();
  writer.loop();
  callback.loop();

}

