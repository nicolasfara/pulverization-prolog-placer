:- set_prolog_flag(stack_limit, 16 000 000 000).
:- set_prolog_flag(last_call_optimisation, true).

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
:- discontiguous link/4.

:- consult('energysourcedata.pl').
:- consult('data.pl').

% Energy and carbon budget per single digital device placement
maxEnergy(20).
maxCarbon(20).
maxNodes(30).

% optimalPlace/3 finds one of the placements with  
% minimal number of nodes, lowest carbon emissions, and last, lowest energy consumption.
optimalPlace(DigDev,p(DigDev,OptC,OptN,OptE,OptP),I) :-
    findall(r(C,N,E,P), (place(DigDev,P,I), footprint(P,E,C,I), involvedNodes(P,_,N)), Placements),
    sort(Placements, SortedPs), SortedPs=[r(OptC,OptN,OptE,OptP)|_],
    maxEnergy(MaxE), maxCarbon(MaxC), maxNodes(MaxN), OptE =< MaxE, OptC =< MaxC, OptN =< MaxN.

footprint(Placement,Energy,Carbon,I) :-
    findall(N, member(on(_,N,_), Placement), Ns), sort(Ns, Nodes),
    hardwareFootprint(Nodes,Placement,Energy,Carbon,I).

hardwareFootprint([N|Ns],Placement,Energy,Carbon,I) :-
    hardwareFootprint(Ns,Placement,EnergyNs,CarbonNs,I),
    nodeEnergy(N,Placement,EnergyN,I), 
    energySourceMix(N,Sources), nodeEmissions(Sources,EnergyN,CarbonN),
    Energy is EnergyN+EnergyNs, Carbon is CarbonN+CarbonNs.
hardwareFootprint([],_,0,0,_).

% Considers how much the Placement increases the energy consumption of node N
% w.r.t. the energy consumption of N before the Placement
% Note: some placements may "consume" null energy as they do not alter enough the infrastucture usage
nodeEnergy(N,Placement,Energy,I):-
    physicalDevice(N, FreeHW, TotHW, _, _), pue(N,PUE),
    (member(used(N,UsedHW), I); \+member(used(N,_), I), UsedHW = 0), 
    OldL is 100 * (TotHW - FreeHW + UsedHW) / TotHW, energyConsumption(N,OldL,OldE),
    findall(H,member(on(_,N,H),Placement),HWs), sum_list(HWs,PHW),
    NewL is 100 * (TotHW - FreeHW + PHW + UsedHW) / TotHW, energyConsumption(N,NewL,NewE),
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
place(DigDev, Placement, I) :-
    digitalDevice(DigDev, K, Components),
    placeKnowledge(K,N,KonN,I),
    placeComponents(Components,N,[KonN],Placement,I).
    % connectivityOk(Placement). % see notes below

% placeKnowledge/2 places a knowledge component K onto a node N that
% supports its hardware requirements.
placeKnowledge(K,N,on(K,N,HWReqs), I) :-
    knowledge(K, HWReqs),
    physicalDevice(N, HWCaps, _, _, _),
    ( member(used(N,HWUsed), I); \+member(used(N,_), I), HWUsed = 0 ),
    HWReqs =< HWCaps - HWUsed.

% hPlace/3 finds a placement of a digital device DigDev that satisfies
% all constraints and does not exceed energy and carbon budgets.
% It returns the number M of involved Nodes, and their list.
quickPlace(DigDev, p(DigDev,C,M,E,Placement), I) :-
    digitalDevice(DigDev, K, Components),
    placeKnowledge(K, N, KonN, I),
    placeComponents(Components,N,[KonN],Placement, I),
    footprint(Placement,E,C,I), involvedNodes(Placement,_,M),
    maxEnergy(MaxE), maxCarbon(MaxC), maxNodes(MaxM), 
    E =< MaxE, C =< MaxC, M =< MaxM.

% placeComponents/4 suitably places components [S, A, B, C] onto nodes
% that support their hardware requirements, and the requirements on
% latency towards the node where the component K is placed. 
% Note: cumulative hardware consumption is checked incrementally
placeComponents([C|Cs],NK,Placement,NewPlacement,I):-
    member(on(_,N,_), Placement), physicalDevice(N, HWCaps, _, Sensors, Actuators),
    (
        (sense(C, HWReqs, LatToK), member((C,_), Sensors)); (act(C, HWReqs, LatToK), member((C,_), Actuators))
    ),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs,I),
    placeComponents(Cs,NK,[on(C,N,HWReqs)|Placement],NewPlacement,I).
placeComponents([C|Cs],NK,Placement,NewPlacement,I):-
    physicalDevice(N, HWCaps, _, Sensors, Actuators), \+ member(on(_,N,_), Placement),
    (
        (sense(C, HWReqs, LatToK), member((C,_), Sensors)); (act(C, HWReqs, LatToK), member((C,_), Actuators))
    ),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs,I),
    placeComponents(Cs,NK,[on(C,N,HWReqs)|Placement],NewPlacement,I).
placeComponents([C|Cs],NK,Placement,NewPlacement,I):-
    member(on(_,N,_), Placement), physicalDevice(N, HWCaps, _, _, _),
    (behaviour(C, HWReqs, LatToK); communication(C, HWReqs, LatToK)),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs,I),
    placeComponents(Cs,NK,[on(C,N,HWReqs)|Placement],NewPlacement,I).
placeComponents([C|Cs],NK,Placement,NewPlacement,I):-
    physicalDevice(N, HWCaps, _, _, _), \+ member(on(_,N,_), Placement),
    (behaviour(C, HWReqs, LatToK); communication(C, HWReqs, LatToK)),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs,I),
    placeComponents(Cs,NK,[on(C,N,HWReqs)|Placement], NewPlacement,I).
placeComponents([],_,P,P,_).

% hwOK/4 holds if all components placed at node N by Placement do not exceed 
% the current capacity of N when adding a new component that requires HWReqs.
hwOK(N,Placement,HWCaps,HWReqs,I) :-
    findall(H, member(on(_,N,H),Placement), Hs), sumlist(Hs,HWAlloc), 
    ( member(used(N,UsedHW), I); \+ member(used(N,_), I), UsedHW = 0 ),
    HWReqs =< HWCaps - HWAlloc - UsedHW.

% latencyOK/3 holds if the link between N and M supports the latency 
% requirements LatReq
latencyOK(N,M,LatReq) :- (eLink(N,M,Lat,_); eLink(M,N,Lat,_)), Lat =< LatReq.

%%% UTILITIES %%%

eLink(X,Y,BW,Lat) :- link(X,Y,BW,Lat).
link(X,X,0,inf). % self-link with infinite bw and null latency

involvedNodes(P,Nodes,M) :-
    findall(N, member(on(_,N,_), P), Ns), list_to_set(Ns, Nodes), length(Nodes, M).

% placeAll/2 finds all the possible placements of a digital device
% 'opt' mode exploits optimal placement, 'heu' mode exploits heuristic placement
placeAll(Mode, Placements) :- 
    findall(DigDev, digitalDevice(DigDev, _, _), Devices), % TODO: heuristics?
    placeDigitalDevices(Mode, Devices, Placements, [], _),
    % p(DigDev,C,M,E,Placement)
    findall(C, member(p(_,C,_,_,_), Placements), Cs), sum_list(Cs, TotC),
    findall(E, member(p(_,_,_,E,_), Placements), Es), sum_list(Es, TotE),
    write('Total Carbon: '), write(TotC), nl, write('Total Energy: '), write(TotE), nl,
    findall(N, member(p(_,_,N,_,_), Placements), Ns), length(Placements, M), sum_list(Ns, TotN),
    write('Avg nodes per placement: '), AvgN is TotN / M, write(AvgN), nl.

placeDigitalDevices(heu, [DigDev|Rest], [P|PRest], IOld, INew) :-
    quickPlace(DigDev, P, IOld),
    updatedInfrastructure(P, IOld, ITmp),
    placeDigitalDevices(heu,Rest,PRest,ITmp,INew).
placeDigitalDevices(opt, [DigDev|Rest], [P|PRest], IOld, INew) :-
    optimalPlace(DigDev, P, IOld),
    updatedInfrastructure(P, IOld, ITmp),
    placeDigitalDevices(opt,Rest,PRest,ITmp,INew).
placeDigitalDevices(_,[],[],I,I).

updatedInfrastructure(p(_,_,_,_,P), I, INew) :-
    involvedNodes(P,Nodes,_), nodesUsage(Nodes, P, I, I, INew).
    
nodesUsage([N|Ns], P, I, IOld, INew) :-
    \+ member(used(N,_), I), 
    findall(H, member(on(_,N,H), P), Hs), sum_list(Hs,PAllocaAtN), 
    nodesUsage(Ns, P, I, [used(N,PAllocaAtN)|IOld], INew).
nodesUsage([N|Ns], P, I, IOld, INew) :-
    member(used(N,UsedHW), I),
    findall(H, member(on(_,N,H), P), Hs), sum_list(Hs,PAllocaAtN),
    NewUsedHW is UsedHW + PAllocaAtN,
    select(used(N,UsedHW), IOld, used(N,NewUsedHW), ITmp),
    nodesUsage(Ns, P, I, ITmp, INew).
nodesUsage([],_,_,I,I).



% Checks that all components of a digital device can communicate according to a chosen Placement
% Might be useful for finer-grained checks on newer DAG-based pulverisation models. 
% In this settings, we assume that all components can communicate through the knowledge component 
% to which all other components connect (see predicate latencyOK/3 in placeComponents/4). 
% %     connectivityOk(Placement) :-
% %         \+ (member(on(C1,N1,_), Placement), member(on(C2,N2,_), Placement), dif(C1,C2), \+ link(N1,N2,_,_)).