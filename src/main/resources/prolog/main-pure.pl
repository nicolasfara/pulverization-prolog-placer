

:- set_prolog_flag(stack_limit, 16 000 000 000).
:- set_prolog_flag(last_call_optimisation, true).

:- discontiguous placeDigitalDevices/6.

:- consult('energysourcedata.pl').
:- consult('data.pl').

% Energy and carbon budget per single digital device placement
maxEnergy(500).
maxCarbon(100).
maxNodes(6).

% optimalPlace/3 finds one of the placements with  
% minimal number of nodes, lowest carbon emissions, and last, lowest energy consumption.
optimalPlace(DigDev,Nodes,p(DigDev,OptC,OptN,OptE,OptP),I) :-
    findall(r(C,N,E,P), (place(DigDev,Nodes,P,I), footprint(P,E,C,I), involvedNodes(P,_,N)), Placements),
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
% Note: some placements may "consume" null energy as they do not alter enough the infrastructure usage
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
place(DigDev, Nodes, Placement, I) :-
    digitalDevice(DigDev, K, Components),
    member((_,N),Nodes), placeKnowledge(K,N,KonN,I),
    placeComponents(Nodes,Components,N,[KonN],Placement,I).
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
quickPlace(DigDev, Nodes, p(DigDev,C,M,E,Placement), I) :-
    digitalDevice(DigDev, K, Components),
    member((_,N),Nodes), placeKnowledge(K, N, KonN, I),
    placeComponents(Nodes,Components,N,[KonN],Placement, I),
    % write(Placement), nl,
    footprint(Placement,E,C,I), involvedNodes(Placement,_,M),
    % writeln(E), writeln(C), writeln(M),
    maxEnergy(MaxE), maxCarbon(MaxC), maxNodes(MaxM), 
    E =< MaxE, C =< MaxC, M =< MaxM.

% placeComponents/4 suitably places components [S, A, B, C] onto nodes
% that support their hardware requirements, and the requirements on
% latency towards the node where the component K is placed. 
% Note: cumulative hardware consumption is checked incrementally
placeComponents(Nodes,[C|Cs],NK,Placement,NewPlacement,I):-
    member(on(_,N,_), Placement), physicalDevice(N, HWCaps, _, Sensors, Actuators),
    (
        (sense(C, HWReqs, LatToK), member((C,_), Sensors)); (act(C, HWReqs, LatToK), member((C,_), Actuators))
    ),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs,I),
    placeComponents(Nodes,Cs,NK,[on(C,N,HWReqs)|Placement],NewPlacement,I).
placeComponents(Nodes,[C|Cs],NK,Placement,NewPlacement,I):-
    member((_,N),Nodes), \+ member(on(_,N,_), Placement), physicalDevice(N, HWCaps, _, Sensors, Actuators), 
    (
        (sense(C, HWReqs, LatToK), member((C,_), Sensors)); (act(C, HWReqs, LatToK), member((C,_), Actuators))
    ),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs,I),
    placeComponents(Nodes,Cs,NK,[on(C,N,HWReqs)|Placement],NewPlacement,I).
placeComponents(Nodes, [C|Cs],NK,Placement,NewPlacement,I):-
    member(on(_,N,_), Placement), physicalDevice(N, HWCaps, _, _, _),
    (behaviour(C, HWReqs, LatToK); communication(C, HWReqs, LatToK)),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs,I),
    placeComponents(Nodes,Cs,NK,[on(C,N,HWReqs)|Placement],NewPlacement,I).
placeComponents(Nodes, [C|Cs],NK,Placement,NewPlacement,I):-
    member((_,N),Nodes), \+ member(on(_,N,_), Placement), physicalDevice(N, HWCaps, _, _, _), 
    (behaviour(C, HWReqs, LatToK); communication(C, HWReqs, LatToK)),
    latencyOK(N,NK,LatToK),
    hwOK(N,Placement,HWCaps,HWReqs,I),
    placeComponents(Nodes,Cs,NK,[on(C,N,HWReqs)|Placement], NewPlacement,I).
placeComponents(_,[],_,P,P,_).

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
eLink(X,X,0,inf). % self-link with infinite bw and null latency

involvedNodes(P,Nodes,M) :-
    findall(N, member(on(_,N,_), P), Ns), list_to_set(Ns, Nodes), length(Nodes, M).

% placeAll/2 finds all the possible placements of a digital device
% 'opt' mode exploits optimal placement, 'heu' mode exploits heuristic placement
placeAll(Mode, Placements, TotE, TotC) :- 
    findall(DigDev, digitalDevice(DigDev, _, _), Devices), 
    greenestNodes(Nodes),
    placeDigitalDevices(Mode, Nodes, Devices, Placements, [], _),
    findall(C, member(p(_,C,_,_,_), Placements), Cs), sum_list(Cs, TotC),
    findall(E, member(p(_,_,_,E,_), Placements), Es), sum_list(Es, TotE).
placeAll(edge, Placements, TotE, TotC) :- 
    findall(DigDev, digitalDevice(DigDev, _, _), Devices), 
    greenestNodes(Nodes),
    placeDigitalDevices(edge, Nodes, Devices, Placements, [], _),
    findall(C, member(p(_,C,_,_,_), Placements), Cs), sum_list(Cs, TotC),
    findall(E, member(p(_,_,_,E,_), Placements), Es), sum_list(Es, TotE).

edgePlace(DigDev, Nodes, p(DigDev, Carb, M, E, Placement), I) :-
    digitalDevice(DigDev, K, Components),
    member(C, Components), member((_,N), Nodes), 
    physicalDevice(N, _, _, Sensors, _), sense(C,_,_), member((C,_), Sensors), 
    placeAllOnN(N, [K|Components], [], Placement, I),
    footprint(Placement, E, Carb, I), 
    involvedNodes(Placement, _, M),
    maxEnergy(MaxE), maxCarbon(MaxC), maxNodes(MaxM), 
    E =< MaxE, Carb =< MaxC, M =< MaxM.

cloudPlace(DigDev, p(DigDev, Carb, M, E, Placement), I) :-
    digitalDevice(DigDev, _, Components),
    member(A, Components), physicalDevice(NA, _, _, _, Actuators), act(A,HWA,_), member((A,_), Actuators), 
    member(S, Components), physicalDevice(NS,_,_,Sensors,_), sense(S,HWS,_), member((S,_), Sensors),
    subtract(Components, [A,S], Rest),
    placeAllOnN(cloud0, Rest, [on(S,NS,HWS),on(S,NA,HWA)], Placement, I), % Note: cloud is the id of the only cloud node
    footprint(Placement, E, Carb, I), 
    involvedNodes(Placement, _, M),
    maxEnergy(MaxE), maxCarbon(MaxC), maxNodes(MaxM), 
    E =< MaxE, Carb =< MaxC, M =< MaxM.

placeAllOnN(N, [C|Cs], Placement, NewPlacement,I) :-
    physicalDevice(N, HWCaps, _, Sensors, Actuators),
    (
        (knowledge(C, HWReqs));
        (sense(C, HWReqs, _), member((C,_), Sensors)); 
        (act(C, HWReqs, _), member((C,_), Actuators)); 
        (behaviour(C, HWReqs, _); communication(C, HWReqs, _))
    ),
    hwOK(N,Placement,HWCaps,HWReqs,I),
    placeAllOnN(N, Cs, [on(C, N, HWReqs)|Placement], NewPlacement, I).
placeAllOnN(_, [], P, P, _).

placeDigitalDevices(heu, Nodes, [DigDev|Rest], [P|PRest], IOld, INew) :-
    quickPlace(DigDev, Nodes, P, IOld),
    updatedInfrastructure(P, IOld, ITmp),
    placeDigitalDevices(heu, Nodes, Rest,PRest,ITmp,INew).
placeDigitalDevices(opt, Nodes, [DigDev|Rest], [P|PRest], IOld, INew) :-
    optimalPlace(DigDev, Nodes, P, IOld),
    updatedInfrastructure(P, IOld, ITmp),
    placeDigitalDevices(opt,Nodes,Rest,PRest,ITmp,INew).
placeDigitalDevices(edge, Nodes, [DigDev|Rest], [P|PRest], IOld, INew) :-
    edgePlace(DigDev, Nodes, P, IOld),
    updatedInfrastructure(P, IOld, ITmp),
    placeDigitalDevices(edge, Nodes, Rest, PRest, ITmp, INew).
placeDigitalDevices(cloud, _, [DigDev|Rest], [P|PRest], IOld, INew) :-
    cloudPlace(DigDev, P, IOld),
    updatedInfrastructure(P, IOld, ITmp),
    placeDigitalDevices(edge, _, Rest, PRest, ITmp, INew).
placeDigitalDevices(_,_,[],[],I,I).

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

greenestNodes(Nodes) :-
    findall((CI,N), (physicalDevice(N,_,_,_,_), carbonIntensity(N,CI)), TmpNodes), sort(TmpNodes, Nodes).

carbonIntensity(N, CI) :-
    energySourceMix(N, Sources),
    multiplyEmissions(Sources, CI).

multiplyEmissions([(P,S)|Srcs], CI) :-
    multiplyEmissions(Srcs, TmpCI),
    emissions(S,MU), CI is P * MU + TmpCI.
multiplyEmissions([],0).

