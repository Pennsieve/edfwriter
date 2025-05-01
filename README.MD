# MEF to EDF

## Usage 
This code converts .mef (MEf2) files into .edf (EDF) files. This is used for time series data, specifically eeg or ieeg data. This code is used to transfer all the data from ieeg.org to Pennsieve. 

## Running

Application expects a single argument as an input.

This argument is a path to a folder where all the MEF files (mef2) are located. The name of the folder is assumed to be the subject ID and will be used as the naming convention. 

If you're building in Eclipse you can go to Run > Run Configurations and add the path as an argument ex: `/Users/ddefreitas/Documents/sub-001/`

With the example path above there are .mef files which the script will pick up and create EDFs for. Each mef file is a channel and the script will produce 1 to x edfs for each channel based on discontinuities.

EDFs will be output in the same input folder.

The code reads in all of the blocks of one MEF channel and calculates the time difference between each block. If the time difference betweeen blocks is greater than two times the sampling frequency, then a cut is created for a new edf. The code then goes back and finds the local min and max across each channel and scales the data according to EDF spec. Once the data is scaled, it is written out to an EDF and the process begins again until all of the mef data is read. 


## Useful tools (Mac OS)

- Hex Fiend for viewing EDF files https://hexfiend.com/
- EDF browser for viewing EDFs https://formulae.brew.sh/cask/edfbrowser

## EDF spec

Basing code off the base EDF spec: https://www.edfplus.info/specs/edf.html


## Time Series Graphing 

The branch TimeSeriesGraphing allows the user to graph subsets of their data in order to validate the efficacy of the converter. This branch does not write out edf, but completes all necessary steps in order to properly calculate scaling. The user will need to update the startrange and endrange values in order to pull out only the block of interest. The time is printed in epoch and can be converted in order to compare to graphed mef data (ex. on ieeg.org).

If using ieeg.org, the start time is set to 0 on the graphing tool and the timing will need to be adjusted in accordance with the real start time of the first edf/mef file. 
