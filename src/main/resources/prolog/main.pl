- set_prolog_flag(answer_write_options,[max_depth(0), spacing(next_argument)]).
:- set_prolog_flag(stack_limit, 16 000 000 000).
:- set_prolog_flag(last_call_optimisation, true).
:- discontiguous link/4.
:- discontiguous physicalDevice/5.
:- discontiguous energyConsumption/3.
:- discontiguous pue/2. 
:- discontiguous energySourceMix/2.
:- consult('energysourcedata.pl').
:- consult('data.pl').

% % maximal energy and carbon budgets
maxEnergy(1).
maxCarbon(3).

link(X,X,0,inf). % self-link with infinite bw and null latency

hPlace(DigDev, Placement, E, C, Nodes) :-
    digitalDevice(DigDev, K, Components),
    placeKnowledge(K, N, KonN),
    placeComponents(Components,N,[KonN],Placement),
    footprint(Placement,E,C), involvedNodes(Placement,_,Nodes).

% optimalPlace/3 finds one of the placements with  
% minimal number of nodes and lowest energy consumption.
optimalPlace(DigDev,OptimalPlacement) :-
    findall(p(N,C,E,P), (place(DigDev,P), footprint(P,E,C), involvedNodes(P,_,N)), Placements),
    sort(Placements, [OptimalPlacement|_]).

involvedNodes(P,Nodes,M) :-
    findall(N, member(on(_,N,_), P), Ns), list_to_set(Ns, Nodes), %writeln(Ns),
    length(Nodes, M).

%   place/2 suitably places a pulverised digital device DigDev onto 
%   a Cloud-IoT continuum. A placement is a set of triples (C,N,H) 
%    where:
%       - C is the component id,
%       - N is the id of its deployment node, 
%       - H is the amount of hardware that C requires at N, and
place(DigDev, Placement) :-
    digitalDevice(DigDev, K, Components),
    placeKnowledge(K,N,KonN),
    placeComponents(Components,N,[KonN],Placement),
    connectivityOk(Placement).

% checks that all components of a digital device can communicate according to a chosen Placement
connectivityOk(Placement) :-
    \+ (member(on(C1,N1,_), Placement), member(on(C2,N2,_), Placement), dif(C1,C2), \+ link(N1,N2,_,_)).

% placeKnowledge/2 places a knowledge component K onto a node N that
% supports its hardware requirements.
placeKnowledge(K,N,on(K,N,HWReqs)) :-
    knowledge(K, HWReqs),
    physicalDevice(N, HWCaps, _, _, _),
    HWReqs =< HWCaps.

% placeComponents/4 suitably places components [S, A, B, C] onto nodes
% that support their hardware requirements, and the requirements on
% latency towards the node where the component K is placed. 
% Note: cumulative hardware consumption is checked incrementally
placeComponents([C|Cs],NK,Placement,NewPlacement):-
    member(on(_,N,_), Placement), physicalDevice(N, HWCaps, _, Sensors, Actuators),
    (
        (sense(C, HWReqs, LatToK), member((C,_), Sensors)); (act(C, HWReqs, LatToK), member((C,_), Actuators))
    ),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs),
    placeComponents(Cs,NK,[on(C,N,HWReqs)|Placement],NewPlacement).
placeComponents([C|Cs],NK,Placement,NewPlacement):-
    physicalDevice(N, HWCaps, _, Sensors, Actuators), 
    (
        (sense(C, HWReqs, LatToK), member((C,_), Sensors)); (act(C, HWReqs, LatToK), member((C,_), Actuators))
    ),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs),
    placeComponents(Cs,NK,[on(C,N,HWReqs)|Placement],NewPlacement).
placeComponents([C|Cs],NK,Placement,NewPlacement):-
    member(on(_,N,_), Placement), physicalDevice(N, HWCaps, _, _, _),
    (behaviour(C, HWReqs, LatToK); communication(C, HWReqs, LatToK)),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs),
    placeComponents(Cs,NK,[on(C,N,HWReqs)|Placement], NewPlacement).
placeComponents([C|Cs],NK,Placement,NewPlacement):-
    physicalDevice(N, HWCaps, _, _, _),
    (behaviour(C, HWReqs, LatToK); communication(C, HWReqs, LatToK)),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs),
    placeComponents(Cs,NK,[on(C,N,HWReqs)|Placement], NewPlacement).
placeComponents([],_,P,P).

footprint(Placement,Energy,Carbon) :-
    findall(N, member(on(_,N,_), Placement), Ns), sort(Ns, Nodes),
    hardwareFootprint(Nodes,Placement,Energy,Carbon).

hardwareFootprint([N|Ns],Placement,Energy,Carbon) :-
    hardwareFootprint(Ns,Placement,EnergyNs,CarbonNs),
    nodeEnergy(N,Placement,EnergyN), 
    energySourceMix(N,Sources), nodeEmissions(Sources,EnergyN,CarbonN),
    Energy is EnergyN+EnergyNs, Carbon is CarbonN+CarbonNs.
hardwareFootprint([],_,0,0).

nodeEnergy(N,Placement,Energy):-
    physicalDevice(N, HW, TotHW, _, _), pue(N,PUE), 
    % OldL is 100 * (TotHW - HW) / TotHW, energyConsumption(N,OldL,OldE),
    findall(H,member(on(_,N,H),Placement),HWs), sum_list(HWs,PHW),
    NewL is 100 * (TotHW - HW + PHW) / TotHW, energyConsumption(N,NewL,NewE),
    Energy is (NewE - OldE) * PUE.

nodeEmissions([(P,S)|Srcs],Energy,Carbon) :-
    nodeEmissions(Srcs,Energy,CarbSrcs),
    emissions(S,MU), CarbS is P * MU * Energy, Carbon is CarbS + CarbSrcs.
nodeEmissions([],_,0).

% hwOK/4 holds if all components placed at node N by Placement do not exceed 
% the current capacity of N when adding a new component that requires HWReqs.
hwOK(N,Placement,HWCaps,HWReqs) :-
    findall(H, member(on(_,N,H),Placement), Hs), sumlist(Hs,UsedHW), HWReqs =< HWCaps - UsedHW.

% latencyOK/3 holds if the link between N and M supports the latency 
% requirements LatReq
latencyOK(N,M,LatReq) :- link(N,M,Lat,_), Lat =< LatReq.

% placeAll/2 finds all the possible placements of a digital device
% 'opt' mode exploits optimal placement, 'heu' mode exploits heuristic placement
placeAll(Mode, Placements) :- 
    findall(DigDev, digitalDevice(DigDev, _, _), Devices), % TODO: heuristics?
    placeAll2(Mode, Devices, Placements).

placeAll2(heu, [DigDev|Rest], [P|PRest]) :-
    hPlace(DigDev, P, _,_,_),
    placeAll2(heu,Rest,PRest).
placeAll2(opt, [DigDev|Rest], [P|PRest]) :-
    optimalPlace(DigDev, P),
    placeAll2(opt,Rest,PRest).
placeAll2(_,[],[]).

%%% TODOs
% @SF
% TODO: deploymentUnit(Type, Id, HWReqs, LatToK). ?

% @RC
% TODO: generare specifiche di physical devices e di logical devices    
    % nodi applicativi: business logic dell'applicazione
    % nodi infrastrutturali: runtime support a tutto il sistema

% TODO: continuous reasoning necessario per gestire dinamismo del programma aggregato e della rete
% TODO: continuous reasoning + adaptation overhead per ciascun componente
% TODO: how to embed adaptive frequency of updates? 
% TODO: add bw in the digital device components?   
% TODO: check "correctness" of the specification


/*
Generazione di deployment di logical device all'interno di sistemi di aggregate computing p olverizzati che ottimizzano consumo energetico attraverso specifica dichiarativa.
PROs:

- dichiaratività sia per esprimere requisiti che vincoli come proprietà sul sistema
- estensibilità
- possibilità di scrivere policy ad-hoc
- applicabilità a "sotto-area"

CONTRO:

- ricerca esaustiva (costosa!)
- vincoli numerici ~
- per ora non incrementale il calcolo dei costi ...

Alternative:

- policy statica non adattiva (all-edge, all-cloud)
- MILP - meno leggibile
- SAT-solvers 
*/

/* TOY EXAMPLE:
% % digitalDevice(Id, K, [S, A, B, C]).
digitalDevice(d1, kd1, [s1, a1, b1, c1]).

% Pulverised components are represented as in:
% knowledge(DId, HWReqs).
knowledge(kd1, 1). 
% behaviour(BId, HWReqs, LatToK).
behaviour(b1, 1, 10).
% communication(CId, HWReqs, LatToK).
communication(c1, 1, 20).
% sense(PhySense, HWReqs, LatToK).
sense(s1, 0.25, 5).
% act(AId, HWReqs, LatToK).
act(a1, 0.25, 5).

% link(N1,N2,Latency,Bandwidth). % end-to-end links
link(robot1, robot2, 4, 50).
link(robot2, robot1, 4, 50).
link(robot2, cloudvm, 4, 50).
link(cloudvm, robot2, 4, 50).
link(robot1, cloudvm, 4, 50).
link(cloudvm, robot1, 4, 50).

%% Hardware
% physicalDevice(Id, AvailableHWCaps, TotalHWCaps, ReachableSensors, ReachableActuators).
%link(N1, N2, Latency, Bandwidth).
% energyConsumption(N, Load, EnergyPerLoad) :- ...
physicalDevice(robot1, 0.3, 5, [(s1, temperature)], [(a1, thermostate)]).
    energySourceMix(robot1,[(0.8,coal), (0.2,solar)]).
    pue(robot1,1.2).
    energyConsumption(robot1, L, 0.2) :- L < 20.
    energyConsumption(robot1, L, 0.3) :- L >= 20.

physicalDevice(robot2, 0.3, 5, [(s1, temperature),(s2, temperature)],[(a1, thermostate),(a2, thermostate)]).
    energySourceMix(robot2,[(0.1,gas),(0.8,coal),(0.1,onshorewind)]).
    pue(robot2,1.2).
    energyConsumption(robot2, L, 0.1) :- L < 2.
    energyConsumption(robot2, L, 0.8) :- L >= 2.

physicalDevice(cloudvm, 28, 30, [], []).
    energySourceMix(cloudvm,[(0.3,solar), (0.7,coal)]).
    pue(cloudvm,1.3).
    energyConsumption(cloudvm, L, 0.5) :- L < 10.
    energyConsumption(cloudvm, L, 1) :- L >= 10.
*/