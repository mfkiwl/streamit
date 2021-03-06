#include "filterdefs.h"
#include <math.h>
#include <stdio.h>
#include "filterstate.h"

#define FILTER_NAME 2
#define HAS_STATE
#define ITEM_TYPE   int
#include "beginfilter.h"

BEGIN_WORK_FUNC
{
  int __WORKCOUNTER__;
  int ___POP_BUFFER_1_1__643[66];
  int ___PUSH_INDEX_1_1__645;
  int ___POP_BUFFER_1_1__348__405__655[8];
  int ___POP_INDEX_1_1__349__406__656;
  int ___PUSH_INDEX_1_1__350__407__657;
  int motion_code__301__354__411__661[2][2][2];
  int t__conflict__8__302__355__412__662;
  int s__conflict__7__303__356__413__663;
  int r__conflict__6__304__357__414__664;
  int motion_residual__305__358__415__665[2][2][2];
  int t__conflict__5__306__359__416__666;
  int s__conflict__4__307__360__417__667;
  int r__conflict__3__308__361__418__668;
  int vectorp__309__362__419__669[2][2][2]={0}; // *** HACK - compiler should do this?
  int delta__310__363__420__670;
  int __tmp21__311__364__421__671;
  int __tmp22__312__365__422__672;
  int __tmp23__313__366__423__673;
  int __tmp24__314__367__424__674;
  float __tmp25__315__368__425__675;
  float __tmp26__316__369__426__676;
  int __tmp27__317__370__427__677;
  int __tmp28__318__371__428__678;
  int prediction__319__372__429__679;
  int t__conflict__2__320__373__430__680;
  int s__conflict__1__321__374__431__681;
  int r__conflict__0__322__375__432__682;
  int t__323__376__433__683;
  int s__324__377__434__684;
  int r__325__378__435__685;
  int __tmp100__326__379__436__686;
  int dataArray__330__380__437__687[8];
  int i__conflict__0__331__381__438__688;
  int i__332__382__439__689;
  int j__333__383__440__690;
  int dataArray__387__441__691[3];
  int i__conflict__0__388__442__692;
  int i__389__443__693;
  int j__390__444__694;
  int _weights__448__695[2] = {8, 3};;
  int _partialSum__449__696[2] = {0, 48};;
  int _k__450__697;
  int _i__451__698;
  int _j__452__699;
  {
    {
      {
        for ((__WORKCOUNTER__ = 0); (__WORKCOUNTER__ < 1); (__WORKCOUNTER__++)) {{
            // mark begin: filter Fused_Ano_Pos__700_324.work__454__630

            (___PUSH_INDEX_1_1__645 = -1);
            {
              {
                (___POP_INDEX_1_1__349__406__656 = -1);
                (___PUSH_INDEX_1_1__350__407__657 = -1);
                {
                  for ((r__conflict__6__304__357__414__664 = 0); (r__conflict__6__304__357__414__664 < 2); (r__conflict__6__304__357__414__664++)) {for ((s__conflict__7__303__356__413__663 = 0); (s__conflict__7__303__356__413__663 < 2); (s__conflict__7__303__356__413__663++)) {for ((t__conflict__8__302__355__412__662 = 0); (t__conflict__8__302__355__412__662 < 2); (t__conflict__8__302__355__412__662++)) {((((motion_code__301__354__411__661[(int)r__conflict__6__304__357__414__664])[(int)s__conflict__7__303__356__413__663])[(int)t__conflict__8__302__355__412__662]) = pop());
                      }
                    }
                  }
                  for ((r__conflict__3__308__361__418__668 = 0); (r__conflict__3__308__361__418__668 < 2); (r__conflict__3__308__361__418__668++)) {for ((s__conflict__4__307__360__417__667 = 0); (s__conflict__4__307__360__417__667 < 2); (s__conflict__4__307__360__417__667++)) {for ((t__conflict__5__306__359__416__666 = 0); (t__conflict__5__306__359__416__666 < 2); (t__conflict__5__306__359__416__666++)) {((((motion_residual__305__358__415__665[(int)r__conflict__3__308__361__418__668])[(int)s__conflict__4__307__360__417__667])[(int)t__conflict__5__306__359__416__666]) = pop());
                      }
                    }
                  }
                  for ((r__conflict__0__322__375__432__682 = 0); (r__conflict__0__322__375__432__682 < 1); (r__conflict__0__322__375__432__682++)) {for ((s__conflict__1__321__374__431__681 = 0); (s__conflict__1__321__374__431__681 < 2); (s__conflict__1__321__374__431__681++)) {for ((t__conflict__2__320__373__430__680 = 0); (t__conflict__2__320__373__430__680 < 2); (t__conflict__2__320__373__430__680++)) {{
                          if ((0 || ((((motion_code__301__354__411__661[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]) == 0))) {(delta__310__363__420__670 = (((motion_code__301__354__411__661[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680])); } else {{
                            (__tmp28__318__371__428__678 = (((motion_code__301__354__411__661[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]));
                            (__tmp27__317__370__427__677 = fabs(__tmp28__318__371__428__678));
                            (__tmp26__316__369__426__676 = ((float)(__tmp27__317__370__427__677)));
                            (__tmp25__315__368__425__675 = (__tmp26__316__369__426__676 - ((float)1.0)));
                            (__tmp24__314__367__424__674 = ((int)(__tmp25__315__368__425__675)));
                            (__tmp23__313__366__423__673 = (__tmp24__314__367__424__674 * 16384));
                            (__tmp22__312__365__422__672 = (__tmp23__313__366__423__673 + (((motion_residual__305__358__415__665[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680])));
                            (__tmp21__311__364__421__671 = (__tmp22__312__365__422__672 + 1));
                            (delta__310__363__420__670 = __tmp21__311__364__421__671);
                            if (((((motion_code__301__354__411__661[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]) < 0)) {(delta__310__363__420__670 = (-delta__310__363__420__670));
                            } else {}
                          }}
                          (prediction__319__372__429__679 = ((((state.PMV__298__337__392__628)[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]));
                          if (((0 && (t__conflict__2__320__373__430__680 == 1)) && 0)) {
                            // TIMER_PRINT_CODE: __print_sink__ += (int)("Error - Program Limitation: May not be correct in decoding motion vectors"); 
                            printf( "%s", "Error - Program Limitation: May not be correct in decoding motion vectors"); printf("\n");

                          } else {}
                          ((((vectorp__309__362__419__669[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]) = (prediction__319__372__429__679 + delta__310__363__420__670));
                          if (((((vectorp__309__362__419__669[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]) < -262144)) {((((vectorp__309__362__419__669[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]) = ((((vectorp__309__362__419__669[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]) + 524288));
                          } else {}
                          if (((((vectorp__309__362__419__669[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]) > 262143)) {((((vectorp__309__362__419__669[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]) = ((((vectorp__309__362__419__669[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]) - 524288));
                          } else {}
                          if (((0 && (t__conflict__2__320__373__430__680 == 1)) && 0)) {
                            // TIMER_PRINT_CODE: __print_sink__ += (int)("Error - Program Limitation: May not be correct in decoding motion vectors"); 
                            printf( "%s", "Error - Program Limitation: May not be correct in decoding motion vectors"); printf("\n");

                          } else {(((((state.PMV__298__337__392__628)[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]) = (((vectorp__309__362__419__669[(int)r__conflict__0__322__375__432__682])[(int)s__conflict__1__321__374__431__681])[(int)t__conflict__2__320__373__430__680]));}
                        }
                      }
                    }
                  }
                  for ((r__325__378__435__685 = 0); (r__325__378__435__685 < 2); (r__325__378__435__685++)) {for ((s__324__377__434__684 = 0); (s__324__377__434__684 < 2); (s__324__377__434__684++)) {for ((t__323__376__433__683 = 0); (t__323__376__433__683 < 2); (t__323__376__433__683++)) {{
                          (__tmp100__326__379__436__686 = (((vectorp__309__362__419__669[(int)r__325__378__435__685])[(int)s__324__377__434__684])[(int)t__323__376__433__683]));
                          ((___POP_BUFFER_1_1__348__405__655[(int)(++___PUSH_INDEX_1_1__350__407__657)]) = __tmp100__326__379__436__686);
                        }
                      }
                    }
                  }
                }
                {
                  for ((i__conflict__0__331__381__438__688 = 0); (i__conflict__0__331__381__438__688 < 8); (i__conflict__0__331__381__438__688++)) {((dataArray__330__380__437__687[(int)i__conflict__0__331__381__438__688]) = (___POP_BUFFER_1_1__348__405__655[(int)(++___POP_INDEX_1_1__349__406__656)]));
                  }
                  for ((j__333__383__440__690 = 0); (j__333__383__440__690 < 6); (j__333__383__440__690++)) {for ((i__332__382__439__689 = 0); (i__332__382__439__689 < 8); (i__332__382__439__689++)) {((___POP_BUFFER_1_1__643[(int)(++___PUSH_INDEX_1_1__645)]) = (dataArray__330__380__437__687[(int)i__332__382__439__689]));
                    }
                  }
                }
              }
              {
                for ((i__conflict__0__388__442__692 = 0); (i__conflict__0__388__442__692 < 3); (i__conflict__0__388__442__692++)) {((dataArray__387__441__691[(int)i__conflict__0__388__442__692]) = pop());
                }
                for ((j__390__444__694 = 0); (j__390__444__694 < 6); (j__390__444__694++)) {for ((i__389__443__693 = 0); (i__389__443__693 < 3); (i__389__443__693++)) {((___POP_BUFFER_1_1__643[(int)(++___PUSH_INDEX_1_1__645)]) = (dataArray__387__441__691[(int)i__389__443__693]));
                  }
                }
              }
            }
            {
              // mark begin: SIRJoiner WEIGHTED_ROUND_ROBIN_Joiner

              {
                {
                  {
                    for ((_k__450__697 = 0); (_k__450__697 < 6); (_k__450__697++)) {{
                        {
                          for ((_i__451__698 = 0); (_i__451__698 < 2); (_i__451__698++)) {{
                              {
                                for ((_j__452__699 = 0); (_j__452__699 < (_weights__448__695[(int)_i__451__698])); (_j__452__699++)) {push((___POP_BUFFER_1_1__643[(int)(0 + ((_k__450__697 * (_weights__448__695[(int)_i__451__698])) + ((_partialSum__449__696[(int)_i__451__698]) + _j__452__699)))]));
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
              // mark end: SIRJoiner WEIGHTED_ROUND_ROBIN_Joiner

            }
            // mark end: filter Fused_Ano_Pos__700_324.work__454__630

          }
        }
      }
    }
  }
}
END_WORK_FUNC

#include "endfilter.h"
