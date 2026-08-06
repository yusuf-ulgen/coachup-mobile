import psycopg2
import sys

passwords = ["Caglar!1517", "Caglar!1517,,"]
ports = [5432, 6543]

connected = False
conn = None

for pw in passwords:
    for port in ports:
        try:
            print(f"Trying pooler with port {port} and password '{pw}'...")
            conn = psycopg2.connect(
                host="aws-0-eu-central-1.pooler.supabase.com",
                database="postgres",
                user="postgres.auiebboyocmkkxbdahqf",
                password=pw,
                port=port,
                connect_timeout=10
            )
            print("Successfully connected!")
            connected = True
            break
        except Exception as e:
            print(f"Failed: {e}")
    if connected:
        break

if not connected:
    print("Could not connect to database with the provided passwords.")
    sys.exit(1)

cur = conn.cursor()
try:
    print("\nFetching Ali Ozturk programs...")
    cur.execute("SELECT id, name, privacy, visible_member_ids FROM training_programs WHERE name ILIKE '%Ali%';")
    rows = cur.fetchall()
    for row in rows:
        print(row)
except Exception as e:
    print(f"Error querying training_programs: {e}")

cur.close()
conn.close()
