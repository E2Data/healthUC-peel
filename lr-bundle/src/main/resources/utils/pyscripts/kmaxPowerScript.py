#!/usr/bin/env python
# -*- coding: utf-8 -*-

import signal
import sys, getopt
import telnetlib
import time
import re

class MyKiller:
    kill_now = False

    def __init__(self):
        signal.signal(signal.SIGINT, self.exit_gracefully)
        signal.signal(signal.SIGTERM, self.exit_gracefully)

    def exit_gracefully(self, signum, frame):
        self.kill_now = True

power = 0
count = 0

tmpFile = sys.argv[1] if len(sys.argv) > 1 else 'con.tmp'

tn = telnetlib.Telnet("10.10.8.92")  # PDU
tn.read_until("Login: ")
tn.write("teladmin\r\n")
tn.read_until("Password: ")
tn.write("telpwd\r\n")
killer = MyKiller()
tn.read_until(">")
while not killer.kill_now:
    tn.write("read meter dev pow simple\r\n")
    try:
        response = tn.read_until(">")
        response = re.sub('[^.0-9]', '', response)
        response = response.strip()
        power = power + float(response)
        count = count + 1
    except Exception as e:
        pass
    time.sleep(0.01)
tn.write("quit\r\n")
totalAvgPowerCons = power / count

print('Total AVG power consumption for KMAX cluster was: %f W' % (totalAvgPowerCons))
f2 = open(tmpFile, "w+")
f2.write(str(totalAvgPowerCons))
f2.close()

sys.exit()