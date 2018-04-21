/* Clark Elliott 2018-04-19

MidiCommandPort = 46789; (But check code for this variable to make sure)

Bascic server to play Midi files for use with the Affective Reasoning agents face display. A number of extensions to functinoality have been listed below.

Currently the AI application sends the name of a MIDI file to play. When we want quiet, we just send a "silent" MIDI file.

It is my hope that we strongly focus on *performed* MIDI recordings. That is, someone sat down with a keyboard and in one or more sessions actually *played* (in real time) the different tracks through the synthesizer into a MIDI recording data file. What we *do not* want is someone just typing in a score to tell the MIDI sequencer when to start playing a note. These sound like boom-chuck elevator music music-boxes and have very constrained emotion content. Plus they make for a poor feeling with demos.

TO USE: Connect to the MidiCommandPort socket with stream data.
Then, each time you send the string name of a midi file followed by newline, it will be played.

Here is the LISP function to do this:
(defun PlayMidi (s) (format MidiStream "~a~%" s))

Current version assumes midi files are in the same directory as the ARMidiServer

----------------------------------------------------

Thanks: https://examples.javacodegeeks.com/desktop-java/sound/play-midi-audio/

Connect to this midi server from the AIApplication. Send the name of the midi file to be played. 
When a new file name is sent, the old one stops playing and the new one starts (currently from the beginning).

> javac ARMidiServer.java
> java ARMidiServer

Note: works fine on my Win7 laptop either way, with fix or without (compile warning):

If you get this Win7 warning:

WARNING: Could not
open/create prefs root node Software\JavaSoft\Prefs at root 0x80000002. Windows RegCreateKeyEx(...) returned error code 5.g

From: https://github.com/julienvollering/MIAmaxent/issues/1

Go into your Start Menu and type regedit into the search field.
2. Navigate to path HKEY_LOCAL_MACHINE\Software\JavaSoft
   (Windows 10 seems to now have this here: HKEY_LOCAL_MACHINE\Software\WOW6432Node\JavaSoft)
3. Right click on the JavaSoft folder and click on New -> Key
4. Name the new Key "Prefs" and everything should work.

All the sequencer methods:
https://docs.oracle.com/javase/7/docs/api/javax/sound/midi/Sequencer.html


Set the volume down so that it doesn't interfere with understanding the speech:

https://stackoverflow.com/questions/18992815/whats-the-method-to-control-volume-in-an-midi-sequencer
https://stackoverflow.com/questions/8008286/how-to-control-the-midi-channels-volume?noredirect=1&lq=1

Note that some midi files change the volume during the course of play, which may defeat setting the volume down at the beginning of play.

Still to do on 2018-04-07:

> Sort out the platform dependent problems with finding which sequencer is being used, so that the volume can ALWAYS get set lower so as not to interfere with speech. This is a nasty problem wherein the defaults used for which of several sequencers are used to play the file don't match up with the defaults used for control methods. Thus you play the midi file, then attempt to change the volume, but this has no effect.

> For additional expression, we might want to play the midi louder when there is no voice, so modify this server so to accept an array of commands, one of which is to reduce the volume when the agent is speaking, or raise it when the agent is no longer speaking (under AI program control).


> May want to have a separate thread with thread-safe code to access th stream (?) and just keep looping through resetting the volume, OR make sure that the midi files we use are cleaned of any internal volume changes.

> Use getTickPosition and setTickPosition(long tick) to keep track of where we are in a sequence, and go back to that location if we start playing the file again. Just create an array of "files played." Whenever we get a new midi file sent to be played (from the AI application, which is choosing the music) then we first check to see if we have it in the array. If so then play from the stored tick mark. Otherwise install in the array and play from the beginning.

When we get the command to stop playing then we store the current tick in the array for this midi file.

> Adapt the AI app command protocol being sent to include playing only a certain portion of the file that matches a particular emotion (so that we can use sections of longer files) by tick mark to start and tick mark to stop.

> I guess set loop to be true, so that if we get to the end of a sequence (including returning to it many times in the course of, say a two-day simulation) we will loop back to the beginning.

> Probably place the MIDI files on a web server and just read the MIDI data in from over the network through the URL.

> At the application end, we might well want to place the files in different subdirectories according to emotion and intensity and then select from any file in that subdir to express that emotion and intensity. This way we get much more variety, and it is easier to just play all the files and assess their appropriateness for expressing that emotion.

> Do the legwork for finding more MIDI files for the different emotions so that we get more variety.

> Extend the command protocol to allow for the changing of instruments being used. The set of instruments will be controlled by the AI app, but set in this MIDI server. As we get more sophisticated, we can have combinations of instruments for different channels.

*/

//package com.javacodegeeks.snippets.desktop;

import java.io.BufferedInputStream;
import java.io.*;
import java.net.*;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequencer;
import javax.sound.midi.*;

//public class l {
public class ARMidiServer {
  static int MidiCommandPort = 46789;
  
  public static void main(String args[]) { // throws Exception {
    System.out.println("Clark Elliott AR MIDI server starting at port " + MidiCommandPort);
    ARMidiServer s = new ARMidiServer(args);
    // l s = new l(args);
    s.run();
  }

  // public l(String args[]){
  public ARMidiServer(String args[]){
  }

  /* Here are the devices available:
     0 :Gervill			
     1 :Microsoft MIDI Mapper
     2 :Microsoft GS Wavetable Synth
     3 :Real Time Sequencer		
  */
      
  Synthesizer synthesizer = null;
  Receiver synthReceiver = null;
  //  Receiver seqReceiver = null;
  //  Receiver x = null;
  Sequencer sequencer = null;
 
  public void run() {
    try{
      InputStream is = null;
      BufferedReader midiIn = null;
      ServerSocket MidiSocket = new ServerSocket(MidiCommandPort);
      Socket MidiSock = MidiSocket.accept();
	
      String midFile = "";
      MidiChannel[] channels = null;
      double gain = 0.2D;
      // initSequencer();
      //MidiDevice.Info[] devices = null;
      
      try{
	// Obtains the default Sequencer connected to a default device:
	MidiDevice.Info[] info = MidiSystem.getMidiDeviceInfo();
	/* This was a nasty problem to solve. It may not adapt to other systems. The problem was
	   (I think) that the sytem does not use the default sequence for the default method calls, so 
	   you cannot control the volume.
	*/
	sequencer = (Sequencer) MidiSystem.getMidiDevice(info[3]);
	sequencer.open(); // Acquire system resources and become operational
	synthesizer = (Synthesizer) MidiSystem.getMidiDevice(info[0]);
	synthesizer.open();
	System.out.println("Synthesizer used: " + synthesizer);
	channels = synthesizer.getChannels();
	System.out.println("Channels: " + channels);
	synthReceiver = synthesizer.getReceiver();
	// seqReceiver = sequencer.getReceiver();
	Transmitter seqTransmitter = sequencer.getTransmitter();
	seqTransmitter.setReceiver(synthReceiver);
      } catch(Exception x) {x.printStackTrace();}
    
      System.out.println("Got a socket connection to MIDI server.");
      midiIn = new BufferedReader(new InputStreamReader(MidiSock.getInputStream()));
      
      while(true){
	midFile = midiIn.readLine();
	try{sequencer.stop();}catch(Exception x) {System.out.println("Stop Error\n");}
	try{is.close();}catch(Exception x) {System.out.println("Close Error\n");}
	System.out.println("Midi Looper got: " + midFile);
	try{
	  is = // Create a stream from a file:
	    new BufferedInputStream(new FileInputStream(new File(midFile)));
	  sequencer.setSequence(is); // Set the sequence from current MIDI data file
	  
	  // Set the volume down for the first half a second...
	  // Have to set the volume down on both of these bit ranges (7, 39):
	  for (int p = 0; p < channels.length; p++) channels[p].controlChange(7, 57); // vol 57 about right...
	  for (int p = 0; p < channels.length; p++) channels[p].controlChange(39, 57);
	  sequencer.start();
	  Thread.sleep(500); // Volume is often set at the beginning of the sequence, so reset after the start
	  for (int p = 0; p < channels.length; p++) channels[p].controlChange(7, 57);
	  for (int p = 0; p < channels.length; p++) channels[p].controlChange(39, 57);
	}catch(Exception x) {x.printStackTrace();} // But keep listening in the loop...
      }
    } catch(Exception x) {x.printStackTrace();}
  }
}


/* These alternate methods might be useful, if the above does not work:	  
ShortMessage full_volume = new ShortMessage();
full_volume.setMessage(ShortMessage.CONTROL_CHANGE, 7, 45);
x.send(full_volume, -1);
synthReceiver.send(full_volume, -1); not this
seqReceiver.send(full_volume, -1); not this
*/
