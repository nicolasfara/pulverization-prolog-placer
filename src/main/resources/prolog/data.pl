
physicalDevice(robot0, 8, 8, [(s0, temperature)], [(a0, thermostate)]).
energyConsumption(robot0, L, 1.4).
energySourceMix(robot0, [(0.4,coal), (0.6,solar)]).
pue(robot0, 1.2).
link(robot0, robot3, 10, 1.4474816724418114).
link(robot0, robot2, 10, 0.9937850121669641).
link(robot0, robot1, 10, 0.9905579879348687).

physicalDevice(robot1, 8, 8, [(s1, temperature)], [(a1, thermostate)]).
energyConsumption(robot1, L, 1.4).
energySourceMix(robot1, [(0.4,coal), (0.6,solar)]).
pue(robot1, 1.2).
link(robot1, robot3, 10, 0.9854299650758181).
link(robot1, robot2, 10, 1.3390943725261581).
link(robot1, robot0, 10, 0.9905579879348687).

physicalDevice(robot2, 8, 8, [(s2, temperature)], [(a2, thermostate)]).
energyConsumption(robot2, L, 1.4).
energySourceMix(robot2, [(0.4,coal), (0.6,solar)]).
pue(robot2, 1.2).
link(robot2, robot3, 10, 0.974070395146285).
link(robot2, robot1, 10, 1.3390943725261581).
link(robot2, robot0, 10, 0.9937850121669641).

physicalDevice(robot3, 8, 8, [(s3, temperature)], [(a3, thermostate)]).
energyConsumption(robot3, L, 1.4).
energySourceMix(robot3, [(0.4,coal), (0.6,solar)]).
pue(robot3, 1.2).
link(robot3, robot2, 10, 0.974070395146285).
link(robot3, robot1, 10, 0.9854299650758181).
link(robot3, robot0, 10, 1.4474816724418114).

physicalDevice(cloud4, 2147483647, 2147483647, [], []).

energyConsumption(cloud4, L, 0.12) :- L < 2.
energyConsumption(cloud4, L, EpL) :- L >= 2, EpL is 0.12 + L*0.2, EpL =< 0.2.
energyConsumption(cloud4, L, 0.2) :- L >= 2, EpL is 0.12 + L*0.2, EpL > 0.2.

energySourceMix(cloud4, [(0.8,coal), (0.2,solar)]).
pue(cloud4, 1.3).
link(cloud4, robot0, 100, 17.47557751965727).
link(cloud4, robot1, 100, 16.72371979911215).
link(cloud4, robot2, 100, 16.791790191599546).
link(cloud4, robot3, 100, 16.030571507941602).

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
    

digitalDevice(d2, kd2, [s2, a2, b2, c2]).
knowledge(kd2, 1).
behaviour(b2, 2, 150).
communication(c2, 0.5, 150).
sense(s2, 0.25, 25).
act(a2, 0.25, 25).
    

digitalDevice(d3, kd3, [s3, a3, b3, c3]).
knowledge(kd3, 1).
behaviour(b3, 2, 150).
communication(c3, 0.5, 150).
sense(s3, 0.25, 25).
act(a3, 0.25, 25).
    
