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

## Appendix A: Selected Use-Cases

### Switch controls Light

There are two devices on the network, a Switch and a Light, where
the Switch has two states and controls the Light.

### Throttle controls Engine

There is a Throttle and an Engine on the network. The Throttle controls
the Engine's output power.

## Appendix B: Comparison with alternatives


