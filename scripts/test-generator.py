#!/usr/bin/env python3
"""
Test GPS position generator — sends simulated vehicle movement to Traccar.
Usage: python3 test-generator.py [--device-id ID] [--host HOST:PORT] [--speed KMH] [--period SECONDS]

Example:
    python3 test-generator.py --device-id demo-vehicle-1 --host localhost:5055 --speed 40 --period 1
"""

import sys
import math
import urllib
import http.client as httplib
import time
import random
import argparse

# Default configuration (matches traccar-fleet-integration/traccar.xml OsmAnd protocol)
id = 'demo-vehicle-1'
server = 'localhost:5055'
period = 1
step = 0.001
device_speed = 40
driver_id = '123456'

# Parse command-line arguments
parser = argparse.ArgumentParser(description='Send simulated GPS positions to Traccar')
parser.add_argument('--device-id', default=id, help=f'Device unique ID (default: {id})')
parser.add_argument('--host', default=server, help=f'Traccar OsmAnd server (host:port, default: {server})')
parser.add_argument('--speed', type=int, default=device_speed, help=f'Device speed in km/h (default: {device_speed})')
parser.add_argument('--period', type=float, default=period, help=f'Seconds between updates (default: {period})')
args = parser.parse_args()

id = args.device_id
server = args.host
device_speed = args.speed
period = args.period

# Waypoints: Asunción, Paraguay (downtown area)
waypoints = [
    (-25.2967, -57.6359),  # Centro
    (-25.2900, -57.6500),  # Sudeste
    (-25.3050, -57.6300),  # Noreste
    (-25.3100, -57.6100),  # Norte
    (-25.3000, -57.5900),  # Noroeste
    (-25.2800, -57.6200)   # Sudoeste
]

points = []

for i in range(0, len(waypoints)):
    (lat1, lon1) = waypoints[i]
    (lat2, lon2) = waypoints[(i + 1) % len(waypoints)]
    length = math.sqrt((lat2 - lat1) ** 2 + (lon2 - lon1) ** 2)
    count = int(math.ceil(length / step))
    for j in range(0, count):
        lat = lat1 + (lat2 - lat1) * j / count
        lon = lon1 + (lon2 - lon1) * j / count
        points.append((lat, lon))

def send(conn, lat, lon, altitude, course, speed, battery, alarm, ignition, accuracy, rpm, fuel, driverUniqueId):
    params = (('id', id), ('timestamp', int(time.time())), ('lat', lat), ('lon', lon), ('altitude', altitude), ('bearing', course), ('speed', speed), ('batt', battery))
    if alarm:
        params = params + (('alarm', 'sos'),)
    if ignition:
        params = params + (('ignition', 'true'),)
    else:
        params = params + (('ignition', 'false'),)
    if accuracy:
        params = params + (('accuracy', accuracy),)
    if rpm:
        params = params + (('rpm', rpm),)
    if fuel:
        params = params + (('fuel', fuel),)
    if driverUniqueId:
        params = params + (('driverUniqueId', driverUniqueId),)
    conn.request('GET', '?' + urllib.parse.urlencode(params))
    conn.getresponse().read()

def course(lat1, lon1, lat2, lon2):
    lat1 = lat1 * math.pi / 180
    lon1 = lon1 * math.pi / 180
    lat2 = lat2 * math.pi / 180
    lon2 = lon2 * math.pi / 180
    y = math.sin(lon2 - lon1) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(lon2 - lon1)
    return (math.atan2(y, x) % (2 * math.pi)) * 180 / math.pi

index = 0

conn = httplib.HTTPConnection(server)

while True:
    (lat1, lon1) = points[index % len(points)]
    (lat2, lon2) = points[(index + 1) % len(points)]
    altitude = 50
    speed = device_speed if (index % len(points)) != 0 else 0
    alarm = (index % 10) == 0
    battery = random.randint(0, 100)
    ignition = (index / 10 % 2) != 0
    accuracy = 100 if (index % 10) == 0 else 0
    rpm = random.randint(500, 4000)
    fuel = random.randint(0, 80)
    driverUniqueId = driver_id if (index % len(points)) == 0 else False
    send(conn, lat1, lon1, altitude, course(lat1, lon1, lat2, lon2), speed, battery, alarm, ignition, accuracy, rpm, fuel, driverUniqueId)
    time.sleep(period)
    index += 1
