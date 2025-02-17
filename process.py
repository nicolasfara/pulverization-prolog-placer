import numpy as np
import xarray as xr
import re
from pathlib import Path
import collections

def distance(val, ref):
    return abs(ref - val)
vectDistance = np.vectorize(distance)

def cmap_xmap(function, cmap):
    """ Applies function, on the indices of colormap cmap. Beware, function
    should map the [0, 1] segment to itself, or you are in for surprises.

    See also cmap_xmap.
    """
    cdict = cmap._segmentdata
    function_to_map = lambda x : (function(x[0]), x[1], x[2])
    for key in ('red','green','blue'):
        cdict[key] = map(function_to_map, cdict[key])
#        cdict[key].sort()
#        assert (cdict[key][0]<0 or cdict[key][-1]>1), "Resulting indices extend out of the [0, 1] segment."
    return matplotlib.colors.LinearSegmentedColormap('colormap',cdict,1024)

def getClosest(sortedMatrix, column, val):
    while len(sortedMatrix) > 3:
        half = int(len(sortedMatrix) / 2)
        sortedMatrix = sortedMatrix[-half - 1:] if sortedMatrix[half, column] < val else sortedMatrix[: half + 1]
    if len(sortedMatrix) == 1:
        result = sortedMatrix[0].copy()
        result[column] = val
        return result
    else:
        safecopy = sortedMatrix.copy()
        safecopy[:, column] = vectDistance(safecopy[:, column], val)
        minidx = np.argmin(safecopy[:, column])
        safecopy = safecopy[minidx, :].A1
        safecopy[column] = val
        return safecopy

def convert(column, samples, matrix):
    return np.matrix([getClosest(matrix, column, t) for t in samples])

def valueOrEmptySet(k, d):
    return (d[k] if isinstance(d[k], set) else {d[k]}) if k in d else set()

def mergeDicts(d1, d2):
    """
    Creates a new dictionary whose keys are the union of the keys of two
    dictionaries, and whose values are the union of values.

    Parameters
    ----------
    d1: dict
        dictionary whose values are sets
    d2: dict
        dictionary whose values are sets

    Returns
    -------
    dict
        A dict whose keys are the union of the keys of two dictionaries,
    and whose values are the union of values

    """
    res = {}
    for k in d1.keys() | d2.keys():
        res[k] = valueOrEmptySet(k, d1) | valueOrEmptySet(k, d2)
    return res

def extractCoordinates(filename):
    """
    Scans the header of an Alchemist file in search of the variables.

    Parameters
    ----------
    filename : str
        path to the target file
    mergewith : dict
        a dictionary whose dimensions will be merged with the returned one

    Returns
    -------
    dict
        A dictionary whose keys are strings (coordinate name) and values are
        lists (set of variable values)

    """
    with open(filename, 'r') as file:
#        regex = re.compile(' (?P<varName>[a-zA-Z._-]+) = (?P<varValue>[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?),?')
        regex = r"(?P<varName>[a-zA-Z._-]+) = (?P<varValue>[^,]*),?"
        dataBegin = r"\d"
        is_float = r"[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?"
        for line in file:
            match = re.findall(regex, line.replace('Infinity', '1e30000'))
            if match:
                return {
                    var : float(value) if re.match(is_float, value)
                        else bool(re.match(r".*?true.*?", value.lower())) if re.match(r".*?(true|false).*?", value.lower())
                        else value
                    for var, value in match
                }
            elif re.match(dataBegin, line[0]):
                return {}

def extractVariableNames(filename):
    """
    Gets the variable names from the Alchemist data files header.

    Parameters
    ----------
    filename : str
        path to the target file

    Returns
    -------
    list of list
        A matrix with the values of the csv file

    """
    with open(filename, 'r') as file:
        dataBegin = re.compile(r'\d')
        lastHeaderLine = ''
        for line in file:
            if dataBegin.match(line[0]):
                break
            else:
                lastHeaderLine = line
        if lastHeaderLine:
            regex = re.compile(r' (?P<varName>\S+)')
            return regex.findall(lastHeaderLine)
        return []

def openCsv(path):
    """
    Converts an Alchemist export file into a list of lists representing the matrix of values.

    Parameters
    ----------
    path : str
        path to the target file

    Returns
    -------
    list of list
        A matrix with the values of the csv file

    """
    regex = re.compile(r'\d')
    with open(path, 'r') as file:
        lines = filter(lambda x: regex.match(x[0]), file.readlines())
        return [[float(x) for x in line.split()] for line in lines]

def beautifyValue(v):
    """
    Converts an object to a better version for printing, in particular:
        - if the object converts to float, then its float value is used
        - if the object can be rounded to int, then the int value is preferred

    Parameters
    ----------
    v : object
        the object to try to beautify

    Returns
    -------
    object or float or int
        the beautified value
    """
    try:
        v = float(v)
        if v.is_integer():
            return int(v)
        return v
    except:
        return v

if __name__ == '__main__':
    # CONFIGURE SCRIPT
    # Where to find Alchemist data files
    directory = 'data'
    # Where to save charts
    output_directory = 'charts'
    # How to name the summary of the processed data
    pickleOutput = 'data_summary'
    # Experiment prefixes: one per experiment (root of the file name)
    experiments = ['prolog-placer']
    floatPrecision = '{: 0.3f}'
    # Number of time samples 
    timeSamples = 360
    # time management
    minTime = 0
    maxTime = 720
    timeColumnName = 'time'
    logarithmicTime = False
    # One or more variables are considered random and "flattened"
    seedVars = ['seed']
    # Label mapping
    class Measure:
        def __init__(self, description, unit = None):
            self.__description = description
            self.__unit = unit
        def description(self):
            return self.__description
        def unit(self):
            return '' if self.__unit is None else f'({self.__unit})'
        def derivative(self, new_description = None, new_unit = None):
            def cleanMathMode(s):
                return s[1:-1] if s[0] == '$' and s[-1] == '$' else s
            def deriveString(s):
                return r'$d ' + cleanMathMode(s) + r'/{dt}$'
            def deriveUnit(s):
                return f'${cleanMathMode(s)}' + '/{s}$' if s else None
            result = Measure(
                new_description if new_description else deriveString(self.__description),
                new_unit if new_unit else deriveUnit(self.__unit),
            )
            return result
        def __str__(self):
            return f'{self.description()} {self.unit()}'
    
    centrality_label = 'H_a(x)'
    def expected(x):
        return r'\mathbf{E}[' + x + ']'
    def stdev_of(x):
        return r'\sigma{}[' + x + ']'
    def mse(x):
        return 'MSE[' + x + ']'
    def cardinality(x):
        return r'\|' + x + r'\|'

    labels = {
        'nodeCount': Measure(r'$n$', 'nodes'),
        'harmonicCentrality[Mean]': Measure(f'${expected("H(x)")}$'),
        'meanNeighbors': Measure(f'${expected(cardinality("N"))}$', 'nodes'),
        'speed': Measure(r'$\|\vec{v}\|$', r'$m/s$'),
        'msqer@harmonicCentrality[Max]': Measure(r'$\max{(' + mse(centrality_label) + ')}$'),
        'msqer@harmonicCentrality[Min]': Measure(r'$\min{(' + mse(centrality_label) + ')}$'),
        'msqer@harmonicCentrality[Mean]': Measure(f'${expected(mse(centrality_label))}$'),
        'msqer@harmonicCentrality[StandardDeviation]': Measure(f'${stdev_of(mse(centrality_label))}$'),
        'org:protelis:tutorial:distanceTo[max]': Measure(r'$m$', 'max distance'),
        'org:protelis:tutorial:distanceTo[mean]': Measure(r'$m$', 'mean distance'),
        'org:protelis:tutorial:distanceTo[min]': Measure(r'$m$', ',min distance'),
    }
    def derivativeOrMeasure(variable_name):
        if variable_name.endswith('dt'):
            return labels.get(variable_name[:-2], Measure(variable_name)).derivative()
        return Measure(variable_name)
    def label_for(variable_name):
        return labels.get(variable_name, derivativeOrMeasure(variable_name)).description()
    def unit_for(variable_name):
        return str(labels.get(variable_name, derivativeOrMeasure(variable_name)))
    
    # Setup libraries
    np.set_printoptions(formatter={'float': floatPrecision.format})
    # Read the last time the data was processed, reprocess only if new data exists, otherwise just load
    import pickle
    import os
    if os.path.exists(directory):
        newestFileTime = max([os.path.getmtime(directory + '/' + file) for file in os.listdir(directory)], default=0.0)
        try:
            lastTimeProcessed = pickle.load(open('timeprocessed', 'rb'))
        except:
            lastTimeProcessed = -1
        shouldRecompute = not os.path.exists(".skip_data_process") and newestFileTime != lastTimeProcessed
        if not shouldRecompute:
            try:
                means = pickle.load(open(pickleOutput + '_mean', 'rb'))
                stdevs = pickle.load(open(pickleOutput + '_std', 'rb'))
            except:
                shouldRecompute = True
        if shouldRecompute:
            timefun = np.logspace if logarithmicTime else np.linspace
            means = {}
            stdevs = {}
            for experiment in experiments:
                # Collect all files for the experiment of interest
                import fnmatch
                allfiles = filter(lambda file: fnmatch.fnmatch(file, experiment + '_*.csv'), os.listdir(directory))
                allfiles = [directory + '/' + name for name in allfiles]
                allfiles.sort()
                # From the file name, extract the independent variables
                dimensions = {}
                for file in allfiles:
                    dimensions = mergeDicts(dimensions, extractCoordinates(file))
                dimensions = {k: sorted(v) for k, v in dimensions.items()}
                # Add time to the independent variables
                dimensions[timeColumnName] = range(0, timeSamples)
                # Compute the matrix shape
                shape = tuple(len(v) for k, v in dimensions.items())
                # Prepare the Dataset
                dataset = xr.Dataset()
                for k, v in dimensions.items():
                    dataset.coords[k] = v
                if len(allfiles) == 0:
                    print("WARNING: No data for experiment " + experiment)
                    means[experiment] = dataset
                    stdevs[experiment] = xr.Dataset()
                else:
                    varNames = extractVariableNames(allfiles[0])
                    for v in varNames:
                        if v != timeColumnName:
                            novals = np.ndarray(shape)
                            novals.fill(float('nan'))
                            dataset[v] = (dimensions.keys(), novals)
                    # Compute maximum and minimum time, create the resample
                    timeColumn = varNames.index(timeColumnName)
                    allData = { file: np.matrix(openCsv(file)) for file in allfiles }
                    computeMin = minTime is None
                    computeMax = maxTime is None
                    if computeMax:
                        maxTime = float('-inf')
                        for data in allData.values():
                            maxTime = max(maxTime, data[-1, timeColumn])
                    if computeMin:
                        minTime = float('inf')
                        for data in allData.values():
                            minTime = min(minTime, data[0, timeColumn])
                    timeline = timefun(minTime, maxTime, timeSamples)
                    # Resample
                    for file in allData:
    #                    print(file)
                        allData[file] = convert(timeColumn, timeline, allData[file])
                    # Populate the dataset
                    for file, data in allData.items():
                        dataset[timeColumnName] = timeline
                        for idx, v in enumerate(varNames):
                            if v != timeColumnName:
                                darray = dataset[v]
                                experimentVars = extractCoordinates(file)
                                darray.loc[experimentVars] = data[:, idx].A1
                    # Fold the dataset along the seed variables, producing the mean and stdev datasets
                    mergingVariables = [seed for seed in seedVars if seed in dataset.coords]
                    means[experiment] = dataset.mean(dim = mergingVariables, skipna=True)
                    stdevs[experiment] = dataset.std(dim = mergingVariables, skipna=True)
            # Save the datasets
            pickle.dump(means, open(pickleOutput + '_mean', 'wb'), protocol=-1)
            pickle.dump(stdevs, open(pickleOutput + '_std', 'wb'), protocol=-1)
            pickle.dump(newestFileTime, open('timeprocessed', 'wb'))
    else:
        means = { experiment: xr.Dataset() for experiment in experiments }
        stdevs = { experiment: xr.Dataset() for experiment in experiments }

    # QUICK CHARTING

    import matplotlib
    import matplotlib.pyplot as plt
    import matplotlib.cm as cmx
    matplotlib.rcParams.update({'axes.titlesize': 12})
    matplotlib.rcParams.update({'axes.labelsize': 10})
    
    def make_line_chart(
        xdata,
        ydata,
        title=None,
        ylabel=None,
        xlabel=None,
        colors=None,
        linewidth=1,
        error_alpha=0.2,
        figure_size=(6, 4)
    ):
        fig = plt.figure(figsize = figure_size)
        ax = fig.add_subplot(1, 1, 1)
        ax.set_title(title)
        ax.set_xlabel(xlabel)
        ax.set_ylabel(ylabel)
#        ax.set_ylim(0)
#        ax.set_xlim(min(xdata), max(xdata))
        index = 0
        for (label, (data, error)) in ydata.items():
#            print(f'plotting {data}\nagainst {xdata}')
            lines = ax.plot(xdata, data, label=label, color=colors(index / (len(ydata) - 1)) if colors else None, linewidth=linewidth)
            index += 1
            if error is not None:
                last_color = lines[-1].get_color()
                ax.fill_between(
                    xdata,
                    data+error,
                    data-error,
                    facecolor=last_color,
                    alpha=error_alpha,
                )
        return (fig, ax)
    def generate_all_charts(means, errors = None, basedir=''):
        viable_coords = { coord for coord in means.coords if means[coord].size > 1 }
        print(viable_coords)
        for comparison_variable in viable_coords - {timeColumnName}:
            mergeable_variables = viable_coords - {timeColumnName, comparison_variable}
            for current_coordinate in mergeable_variables:
                merge_variables = mergeable_variables - { current_coordinate }
                merge_data_view = means.mean(dim = merge_variables, skipna = True)
                merge_error_view = errors.mean(dim = merge_variables, skipna = True)
                for current_coordinate_value in merge_data_view[current_coordinate].values:
                    beautified_value = beautifyValue(current_coordinate_value)
                    for current_metric in merge_data_view.data_vars:
                        title = f'{label_for(current_metric)} for diverse {label_for(comparison_variable)} when {label_for(current_coordinate)}={beautified_value}'
                        for withErrors in [True, False]:
                            fig, ax = make_line_chart(
                                title = title,
                                xdata = merge_data_view[timeColumnName],
                                xlabel = unit_for(timeColumnName),
                                ylabel = unit_for(current_metric),
                                ydata = {
                                    beautifyValue(label): (
                                        merge_data_view.sel(selector)[current_metric],
                                        merge_error_view.sel(selector)[current_metric] if withErrors else 0
                                    )
                                    for label in merge_data_view[comparison_variable].values
                                    for selector in [{comparison_variable: label, current_coordinate: current_coordinate_value}]
                                },
                            )
                            ax.set_xlim(minTime, maxTime)
                            ax.legend()
                            fig.tight_layout()
                            by_time_output_directory = f'{output_directory}/{basedir}/{comparison_variable}'
                            Path(by_time_output_directory).mkdir(parents=True, exist_ok=True)
                            figname = f'{comparison_variable}_{current_metric}_{current_coordinate}_{beautified_value}{"_err" if withErrors else ""}'
                            for symbol in r".[]\/@:":
                                figname = figname.replace(symbol, '_')
                            fig.savefig(f'{by_time_output_directory}/{figname}.pdf')
                            plt.close(fig)
    for experiment in experiments:
        current_experiment_means = means[experiment]
        current_experiment_errors = stdevs[experiment]
        # generate_all_charts(current_experiment_means, current_experiment_errors, basedir = f'{experiment}/all')
        
# Custom charting
    import seaborn as sns

    sns.set_theme(
        style="whitegrid",
        palette="colorblind",
    )

    Path('charts/prolog-placer').mkdir(parents=True, exist_ok=True)

    data = means['prolog-placer'].squeeze()
    dataframe = data.to_dataframe()
    print(dataframe)

    print("geneating chart for Latency")
    # Reset the index for Seaborn usage
    df_reset = dataframe.reset_index()
    # Define the strategies and baseline
    strategies = ["edge", "optimal", "heuristic"]
    baselines = [True, False]
    # Convert baseline to a string for FacetGrid
    df_reset["isBaseline"] = df_reset["isBaseline"].map({True: "Startup Deployment", False: "Continuous Deployment"})
    # Melt the dataframe to long format for Seaborn
    df_melted = df_reset.melt(
        id_vars=["time", "isBaseline", "deploymentStrategy"],
        value_vars=["IntraLatency[mean]", "InterLatency[mean]"],
        var_name="Latency Type",
        value_name="Latency"
    )
    # Replace legend values with custom names
    df_melted["Latency Type"] = df_melted["Latency Type"].replace({
        "IntraLatency[mean]": "Intra Device Latency",
        "InterLatency[mean]": "Inter Device Latency"
    })
    # Create a FacetGrid with 2x2 layout
    g = sns.FacetGrid(
        df_melted,
        col="isBaseline",
        row="deploymentStrategy",
        sharex=True,
        sharey=True,
        aspect=1.5,
        col_order=["Startup Deployment", "Continuous Deployment"],  # Left to right order
        row_order=strategies,  # Top to bottom order
    )
    # Map lineplot for the melted dataframe
    g.map_dataframe(sns.lineplot, x="time", y="Latency", hue="Latency Type")
    # Adjust titles, labels, and legends
    g.set_titles(row_template="Strategy: {row_name}", col_template="{col_name}")
    g.set_axis_labels("Time", "Latency")
    g.add_legend(title="Latency Type"),
    # Save the plot to a file
    g.savefig('charts/prolog-placer/IntraLatency_vs_InterLatency.pdf')

    print("geneating chart for Carbon vs Energy")
    # Melt the dataframe to long format for Seaborn
    df_melted = df_reset.melt(
        id_vars=["time", "isBaseline", "deploymentStrategy"], 
        value_vars=["Carbon[mean]", "Energy[mean]"],
        var_name="Metric", 
        value_name="Value"
    )
    # Replace legend values with custom names
    df_melted["Metric"] = df_melted["Metric"].replace({
        "Carbon[mean]": "Carbon Emission",
        "Energy[mean]": "Energy Consumption"
    })
    # Create FacetGrid with 2x2 layout
    g = sns.FacetGrid(
        df_melted,
        col="isBaseline",
        row="deploymentStrategy",
        sharex=True,
        sharey=True,
        aspect=1.5,
        col_order=["Startup Deployment", "Continuous Deployment"],
        row_order=strategies, 
    )
    # Use map_dataframe with hue to differentiate the metrics
    g.map_dataframe(sns.lineplot, x="time", y="Value", hue="Metric")
    # Set titles and labels
    g.set_titles(row_template="Strategy: {row_name}", col_template="{col_name}")
    g.set_axis_labels("Time", "Value")
    g.add_legend(title="Metric")
    # Save the figure
    g.savefig("charts/prolog-placer/Carbon_vs_Energy.pdf")

    import math
    
    print("geneating chart for Execution Time")
    # Filter out baseline rows
    df_filtered = dataframe[dataframe.index.get_level_values("isBaseline") == False].reset_index()
    # Convert execution time to seconds
    df_filtered["ExecutionTime[mean]"] = df_filtered["ExecutionTime[mean]"] / 1e6
    # Create FacetGrid for subplots
    g = sns.FacetGrid(
        df_filtered,
        col="nodes",  # One subplot per unique node count
        col_wrap=int(math.sqrt(df_filtered["nodes"].nunique())),  # Arrange in a grid
        sharey=True,  # Keep y-axis scale consistent across subplots
        aspect=1.5,
        # height=5  # Adjust subplot size
    )
    # Add line plots
    g.map_dataframe(
        sns.lineplot,
        x="time",
        y="ExecutionTime[mean]",
        hue="deploymentStrategy",
    )
    # Apply log scale & labels in a single call
    g.set(
        yscale="log",
        xlabel="Time",
        ylabel="Execution Time (ms)"
    )
    # Adjust titles & legend
    g.set_titles(col_template="Nodes: {col_name}")
    g.add_legend(title="Deployment Strategy")
    g.set(yscale="symlog", ylim=(0, None))  # Log scale for better visibility if values vary widely
    # Save the figure directly
    g.savefig("charts/prolog-placer/ExecutionTime_evolution.pdf")

    print("geneating chart for Execution Time Scaling")
    # Filter out baseline rows and reset the index
    df_filtered = dataframe[dataframe.index.get_level_values("isBaseline") == False].reset_index()
    # Convert execution time to seconds
    df_filtered["ExecutionTime[mean]"] = df_filtered["ExecutionTime[mean]"] / 1e6  # Convert to ms
    # Create the Seaborn lineplot with error bars
    g = sns.relplot(
        data=df_filtered,
        x="nodes",
        y="ExecutionTime[mean]",
        hue="deploymentStrategy",
        style="deploymentStrategy",  # Different styles if needed
        kind="line",  # Use line plot
        markers=True,  # Adds markers to show actual data points
        dashes=False,  # Disable dashes for lines
        errorbar="sd",  # Show standard deviation as error bars
        aspect=2,  # Set the aspect ratio for the plot
    )
    # Set labels, title, and other properties
    g.set_axis_labels("Number of Nodes", "Average Execution Time (ms)")
    g.set_titles("Execution Time Scaling with Node Count")
    g.set(yscale="symlog", ylim=(0, None))  # Log scale for better visibility if values vary widely
    # g.despine(left=True)  # Remove left spines for a cleaner look
    # # Add legend with a title
    # g._legend.set_title("Deployment Strategy")
    # Save the plot
    g.savefig('charts/prolog-placer/ExecutionTime_scaling.pdf')
