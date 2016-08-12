#include <SoftwareSerial.h>

const byte txPin = 9;
const byte rxPin = 8;

SoftwareSerial bt(rxPin, txPin);

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

}

unsigned long heartbeatMs;

void loop() {
  // put your main code here, to run repeatedly:

  if (millis() - heartbeatMs > 5000) {
    heartbeatMs = millis();
    bt.write("heartbeat\n");
  }

  if (Serial.available()) {
    int ch = Serial.read();
    bt.write((char)ch);
    Serial.print("sending ");
    Serial.println((char)ch);
  }

  if (bt.available()) {
    while (bt.available()) {
      int ch = bt.read();
      Serial.print((char)ch);
      delay(10);
    }
    Serial.println();
  }

}
