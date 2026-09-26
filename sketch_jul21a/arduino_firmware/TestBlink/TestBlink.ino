void setup() {
  Serial.begin(115200);
  Serial.println("START");
}

void loop() {
  Serial.println("ALIVE");
  delay(1000);
}
