
:- discontiguous digitalDevice/3.
:- discontiguous knowledge/2.
:- discontiguous behaviour/3.
:- discontiguous communication/3.
:- discontiguous sense/3.
:- discontiguous act/3.

physicalDevice(robot0, 8, 8, [(s0, temperature)], [(a0, thermostate)]).
energyConsumption(robot0, _, 1.4).
energySourceMix(robot0, [(0.4,coal), (0.6,solar)]).
pue(robot0, 1.2).
link(robot0, robot1, 10, 0.9890515424521616).

physicalDevice(robot1, 8, 8, [(s1, temperature)], [(a1, thermostate)]).
energyConsumption(robot1, _, 1.4).
energySourceMix(robot1, [(0.4,coal), (0.6,solar)]).
pue(robot1, 1.2).
link(robot1, robot0, 10, 0.9890515424521616).

physicalDevice(cloud2, 2147483647, 2147483647, [], []).

energyConsumption(cloud2, L, 0.12) :- L < 2.
energyConsumption(cloud2, L, EpL) :- L >= 2, EpL is 0.12 + L*0.2, EpL =< 0.2.
energyConsumption(cloud2, L, 0.2) :- L >= 2, EpL is 0.12 + L*0.2, EpL > 0.2.

energySourceMix(cloud2, [(0.8,coal), (0.2,solar)]).
pue(cloud2, 1.3).
link(cloud2, robot0, 100, 17.48721490176734).
link(cloud2, robot1, 100, 16.85728022217271).

physicalDevice(cloud3, 2147483647, 2147483647, [], []).

energyConsumption(cloud3, L, 0.12) :- L < 2.
energyConsumption(cloud3, L, EpL) :- L >= 2, EpL is 0.12 + L*0.2, EpL =< 0.2.
energyConsumption(cloud3, L, 0.2) :- L >= 2, EpL is 0.12 + L*0.2, EpL > 0.2.

energySourceMix(cloud3, [(0.8,coal), (0.2,solar)]).
pue(cloud3, 1.3).
link(cloud3, robot0, 100, 17.504873261684693).
link(cloud3, robot1, 100, 16.875468560870424).

digitalDevice(d0, kd0, [s0, a0, b0, c0]).
knowledge(kd0, 1).
behaviour(b0, 2, 150).
communication(c0, 0.5, 150).
sense(s0, 0.25, 25).
act(a0, 0.25, 25).
    

digitalDevice(d1, kd1, [s1, a1, b1, c1]).
knowledge(kd1, 1).
behaviour(b1, 2, 150).
communication(c1, 0.5, 150).
sense(s1, 0.25, 25).
act(a1, 0.25, 25).
    
