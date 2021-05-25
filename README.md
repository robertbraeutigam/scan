# SCAN, Simple Control and Acquisition Network

The SCAN is an open, free, easy-to-use, extensible, DIY-friendly
way to collect data and control devices on a network.

## Primary Goals

These are the main considerations driving the design of this protocol:

- **Security first**, but only to an extent dictated by proper threat modeling.
- **Minimal effort** implementations. The effort to implement compatible devices
  should be linear to the extent of features the device will support. I.e.
  simple devices (like a button) should be almost no effort to implement, while
  more complicated devices may require more thorough analysis and design.
- **Driven by individuals**, not industry. I.e. does not have to support complicated
  use-cases which are almost never used, or used only because of historical reasons.
- **DIY** friendly. No proprietary components, processes or certification requirements.
  A private individual should be able to create a compatible device easily and completely
  independently.
- **Transparent**. It should be very easy to discover which devices need what inputs and react or control
  what other devices. Not out-of-band, like through documentation, but through the actual 
  protocol itself.
- Does **not require** a complete and **perfect list of codes**, nor a perfect usage on the part
  of the devices to be *fully* usable.

## Solution Overview

SCAN is defined on top of the Internet Protocol (IP), so off-the-shelf internet networking tools and
devices can be used to build the physical network, including routers, repeaters, cables
or even WiFi. There is no requirement on the physical network topology, nor on any particular
network service to be available, as long as individual devices can communicate with each other.

All communication is peer-to-peer and end-to-end encrypted with automatically
rotating keys. This means there is no central component or server, and all
communication is secured against third parties.

All devices have a uniform interface, which mainly consist of these things:

* The specification of Data they produce
* The specification of Controls they offer
* A uniform wiring interface

All data is defined as a time-series. That is, each data point has a time
when it was produced, and all the related data elements produced at that instant.
Data elements are not defined in terms of technology (like 32bit or 64bit numbers),
but in terms of what *kind* of data elements they are. Whether they represent an 
Identification (ID) of some sort, or an Event, or a Measurement (see relevant chapter).

Measurements carry unit information (like Liter, PSI, %, etc.) in addition to a name. This
way data elements don't exclusively rely on their "standardized" semantics, and can be uniformly
processed even without the exact meaning they might carry.

Controls are a way to influence the device in some way. That can range from toggling a switch
to sending a remote firmware update. All the controls are listed through the uniform interface,
so they are discoverable on the fly.

Wiring in SCAN is the process of connecting data to controls. 
For example connecting the data from a switch to the on/off
control of a light source. Because the *kind* of both the data and control are known, the connection
can be made even if the exact semantics of either side is unknown or not defined.

Of course auto-wiring can be done using standardized semantics of data, if applicable. Such as 
auto-connecting a Plotter to a GPS Receiver.

## Appendix A: Selected Use-Cases

### Switch controls Light

There are two devices on the network, a Switch and a Light, where
the Switch has two states and controls the Light.

### Throttle controls Engine

There is a Throttle and an Engine on the network. The Throttle controls
the Engine's output power.

### Monitor streaming video from Security Camera

There is a Monitor device which continuously shows the feed coming from
a Security Camera.

