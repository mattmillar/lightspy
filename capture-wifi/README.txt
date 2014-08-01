REQUIREMENTS:

	* mplayer must be installed.

	* GNU truncate must be installed.  This script expects it
	  to be named ``gtruncate''.

	* The directory ``movies'' must exist and contain movies that
	  your installation of mplayer can play.

	* The directory ``sensor-data'' must exist.



INSTRUCTIONS to collect light data in batch:

	1. Run ``nc -l 3000 > wifibuf'' in a separate tab or as a daemon.

	2. Set the phone to dump data to this computer on port 3000.

	3. Press the "Start recording" button on the phone.

	4. Make sure your testing environment is set up right
	   (phone is positioned appropriately, etc).

	5. Run the ``capture-wifi.sh' program on this computer.


Note: you do not have to use port 3000 (that was just an example), but
you *do* have to  perform these steps in order.
Otherwise it won't work.  Thank you!
