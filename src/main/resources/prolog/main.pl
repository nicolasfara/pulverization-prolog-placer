:- set_prolog_flag(stack_limit, 16 000 000 000).
:- set_prolog_flag(last_call_optimisation, true).
:- discontiguous link/4.
:- discontiguous physicalDevice/5.
:- discontiguous energyConsumption/3.
:- discontiguous pue/2. 
:- discontiguous energySourceMix/2.
:- discontiguous digitalDevice/3.
:- discontiguous knowledge/2.
:- discontiguous behaviour/3.
:- discontiguous communication/3.
:- discontiguous sense/3.
:- discontiguous act/3.
:- consult('energysourcedata.pl').
:- consult('data.pl').

% Energy and carbon budget per single digital device placement
maxEnergy(2).
maxCarbon(2).
maxNodes(3).

% optimalPlace/3 finds one of the placements with  
% minimal number of nodes, lowest carbon emissions, and last, lowest energy consumption.
optimalPlace(DigDev,p(OptN,OptC,OptE,OptP)) :-
    findall(p(N,C,E,P), (place(DigDev,P), footprint(P,E,C), involvedNodes(P,_,N)), Placements),
    sort(Placements, [p(OptN,OptC,OptE,OptP)|_]),
    maxEnergy(MaxE), maxCarbon(MaxC), maxNodes(MaxN), OptE =< MaxE, OptC =< MaxC, OptN =< MaxN.

footprint(Placement,Energy,Carbon) :-
    findall(N, member(on(_,N,_), Placement), Ns), sort(Ns, Nodes),
    hardwareFootprint(Nodes,Placement,Energy,Carbon).

hardwareFootprint([N|Ns],Placement,Energy,Carbon) :-
    hardwareFootprint(Ns,Placement,EnergyNs,CarbonNs),
    nodeEnergy(N,Placement,EnergyN), 
    energySourceMix(N,Sources), nodeEmissions(Sources,EnergyN,CarbonN),
    Energy is EnergyN+EnergyNs, Carbon is CarbonN+CarbonNs.
hardwareFootprint([],_,0,0).

% Considers how much the Placement increases the energy consumption of node N
% w.r.t. the energy consumption of N before the Placement
% Note: some placements may "consume" null energy as they do not alter enough the infrastucture usage
nodeEnergy(N,Placement,Energy):-
    physicalDevice(N, HW, TotHW, _, _), pue(N,PUE), 
    OldL is 100 * (TotHW - HW) / TotHW, energyConsumption(N,OldL,OldE),
    findall(H,member(on(_,N,H),Placement),HWs), sum_list(HWs,PHW),
    NewL is 100 * (TotHW - HW + PHW) / TotHW, energyConsumption(N,NewL,NewE),
    Energy is (NewE - OldE) * PUE.

nodeEmissions([(P,S)|Srcs],Energy,Carbon) :-
    nodeEmissions(Srcs,Energy,CarbSrcs),
    emissions(S,MU), CarbS is P * MU * Energy, Carbon is CarbS + CarbSrcs.
nodeEmissions([],_,0).

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

% placeKnowledge/2 places a knowledge component K onto a node N that
% supports its hardware requirements.
placeKnowledge(K,N,on(K,N,HWReqs)) :-
    knowledge(K, HWReqs),
    physicalDevice(N, HWCaps, _, _, _),
    HWReqs =< HWCaps.

% hPlace/3 finds a placement of a digital device DigDev that satisfies
% all constraints and does not exceed energy and carbon budgets.
% It returns the number M of involved Nodes, and their list.
quickPlace(DigDev, p(M,C,E,Placement)) :-
    digitalDevice(DigDev, K, Components),
    placeKnowledge(K, N, KonN),
    placeComponents(Components,N,[KonN],Placement),
    footprint(Placement,E,C), involvedNodes(Placement,_,M),
    maxEnergy(MaxE), maxCarbon(MaxC), maxNodes(MaxM), 
    E =< MaxE, C =< MaxC, M =< MaxM.

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
    physicalDevice(N, HWCaps, _, Sensors, Actuators), \+ member(on(_,N,_), Placement),
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
    physicalDevice(N, HWCaps, _, _, _), \+ member(on(_,N,_), Placement),
    (behaviour(C, HWReqs, LatToK); communication(C, HWReqs, LatToK)),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs),
    placeComponents(Cs,NK,[on(C,N,HWReqs)|Placement], NewPlacement).
placeComponents([],_,P,P).

% Checks that all components of a digital device can communicate according to a chosen Placement
% TODO: si può "affinare"?
connectivityOk(Placement) :-
    \+ (member(on(C1,N1,_), Placement), member(on(C2,N2,_), Placement), dif(C1,C2), \+ link(N1,N2,_,_)).


% hwOK/4 holds if all components placed at node N by Placement do not exceed 
% the current capacity of N when adding a new component that requires HWReqs.
hwOK(N,Placement,HWCaps,HWReqs) :-
    findall(H, member(on(_,N,H),Placement), Hs), sumlist(Hs,UsedHW), HWReqs =< HWCaps - UsedHW.

% latencyOK/3 holds if the link between N and M supports the latency 
% requirements LatReq
latencyOK(N,M,LatReq) :- (link(N,M,Lat,_); link(M,N,Lat,_)), Lat =< LatReq.

%%% UTILITIES %%%

link(X,X,0,inf). % self-link with infinite bw and null latency

involvedNodes(P,Nodes,M) :-
    findall(N, member(on(_,N,_), P), Ns), list_to_set(Ns, Nodes), %writeln(Ns),
    length(Nodes, M).


% % placeAll/2 finds all the possible placements of a digital device
% % 'opt' mode exploits optimal placement, 'heu' mode exploits heuristic placement
% placeAll(Mode, Placements) :- 
%     findall(DigDev, digitalDevice(DigDev, _, _), Devices), % TODO: heuristics?
%     placeDigitalDevices(Mode, Devices, Placements).

% placeDigitalDevices(heu, [DigDev|Rest], [P|PRest]) :-
%     hPlace(DigDev, P, _),
%     placeDigitalDevices(heu,Rest,PRest).
% placeDigitalDevices(opt, [DigDev|Rest], [P|PRest]) :-
%     optimalPlace(DigDev, P),
%     placeDigitalDevices(opt,Rest,PRest).
% placeDigitalDevices(_,[],[]).