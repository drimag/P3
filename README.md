STDISCM S12
DIMAGIBA, Rafael
GARCIA, Aurelio
PARK, Sehyun
SILLONA, Eugene

# Client 
(Main Computer - Windows 10 OS):
1. Ensure Java 17.0.12 or later is installed.
2. Open a terminal/command prompt in the Client folder.
3. Run the client by executing:
	java Client
4. Enter the server(VM's) IP address.
5. Enter the number of producer threads.
6. Select the folders (one per producer thread) containing your MP4 video files.
6.1 I've made 2 folders inside Client folder, add more folder if you want more than 2 Producer Thread

# Server 
(Virtual Machine - Windows OS(10/11)):
1. Ensure that the Server folder is moved to the VM
2. Ensure Java 17.0.12 or later is installed.
3. Run the server by executing the provided run.bat file.
4. Enter the number of consumer threads.
5. Enter the maximum queue length (uploads beyond this limit will be dropped).
6. The server GUI will open, displaying the list of uploaded videos. Hover over a video to preview 10 seconds and click to play it.

# Timestamps
```
0:00 ~ 3:00 -> Demo guidelines and code comparison
3:00 ~ 5:00 -> demo (main features)
	3:00 ~ 4:05 -> overloading server (4 producer threads[8 vids], 2 consumer threads, queue length = 2)
	4:05 ~ 5:00 -> video playback and previews

5:00 ~ 12:09 -> demo (bonus features)
	5:00 ~ 5:25 -> The consumer program can tell the producer processes that the queue is full
 	5:25 ~ 8:18 -> Duplicate Detection
  	8:18 ~ 12:09 -> Video Compression
```
