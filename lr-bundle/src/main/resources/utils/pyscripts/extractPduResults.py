#!/usr/bin/env python
# -*- coding: utf-8 -*-

import re
import sys, getopt
import os

path = sys.argv[1] if len(sys.argv) > 1 else None

output = sys.argv[2] if len(sys.argv) > 2 else "pdu.csv"

result = open(os.path.join(path, output), "w+")
result.write("Worker,TaskSlots,Run,PDU_W\n")

list_subfolders_with_paths = [ name for name in os.listdir(path) if os.path.isdir(os.path.join(path, name)) ]
list_subfolders_with_paths.sort()
for exp_path in list_subfolders_with_paths:
    for root, dirs, files in os.walk(os.path.join(path, exp_path)):
        for file in files:
            if file == "pdu.log":
                print(os.path.join(root, file))
                cons = open(os.path.join(root, file)).read()
                workers = re.search('top(.+?)-', exp_path).group(1).lstrip("0")
                slots = re.search('-(.+?).run', exp_path).group(1)
                run = exp_path.split(".run"
                                     "")[1].lstrip("0")
                result.write(workers + ",")
                result.write(slots + ",")
                result.write(run + ",")
                result.write(cons + "\n")

result.close()
sys.exit()