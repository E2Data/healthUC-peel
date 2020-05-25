#!/usr/bin/env python
# -*- coding: utf-8 -*-

import re
import signal
import sys, getopt
import telnetlib
import time


class MyKiller:
    kill_now = False

    def __init__(self):
        signal.signal(signal.SIGINT, self.exit_gracefully)
        signal.signal(signal.SIGTERM, self.exit_gracefully)

    def exit_gracefully(self, signum, frame):
        self.kill_now = True

powers = {}

apcNodeIds = {'silver1': '1',
              'gold1': '2',
              'gold2': '3',
              'gold3': '4',
              'termi7': '5',
              'termi8': '5',
              'termi9': '6',
              'termi10': '6',
              'termi11': '7',
              'termi12': '7',
              'cognito': '22',
              'quest': '23'}

nodes = ','.join(map(lambda x: apcNodeIds[x], sys.argv[2:])) if len(sys.argv) > 2 else 'all'
tmpFile = sys.argv[1] if len(sys.argv) > 1 else 'con.tmp'

tn = telnetlib.Telnet("147.102.4.150")  # PDU
tn.read_until("User Name : ")
tn.write("e2data\r\n")
tn.read_until("Password  : ")
tn.write("e2datae2datae2data\r\n")
killer = MyKiller()
tn.read_until("apc>")
while not killer.kill_now:
    tn.write("olReading " + nodes + " power\r\n")
    try:
        response = tn.read_until("apc>")
        lines = response.split("\r\n")
        statusLine = lines.index("E000: Success")
        values = lines[1:statusLine]
        for value in values:
            key = value.split(":")[1].strip()
            power = re.sub('[^0-9]', '', value.split(":")[-1])
            if key not in powers.keys():
                powers.setdefault(key, []).append(int(power))
            else:
                powers[key].append(int(power))
    except:
        pass
    time.sleep(0.01)
tn.write("bye\r\n")
totalAvgPowerCons = 0
for key in powers.keys():
    avg = sum(powers[key]) / float(len(powers[key]))
    print('AVG power consumption for %s was %f W' % (key, avg))
    totalAvgPowerCons += avg

print('Total AVG power consumption for: %s was: %f W' % (', '.join(powers.keys()), totalAvgPowerCons))
f2 = open(tmpFile, "w+")
f2.write(str(totalAvgPowerCons))
f2.close()

sys.exit()