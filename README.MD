# MEF to EDF test

## Running

Application expects a single argument as an input.

This argument is a path to where and EDF file is located.

If you're building in Eclipse you can go to Run > Run Configurations and add the path as an argument ex: `/Users/ddefreitas/Documents/LC02/`

With the example path above there are .mef files which the script will pick up and create EDFs for. Each mef file is a channel and the script will produce 1 to x edfs for each channel based on discontinuities.

EDFs will be output in the same input folder. Along with a CSV of shifted times.


## Useful tools (Mac OS)

- Hex Fiend for viewing EDF files https://hexfiend.com/
- EDF browser for viewing EDFs https://formulae.brew.sh/cask/edfbrowser

## EDF spec

Basing code off the base EDF spec: https://www.edfplus.info/specs/edf.html
