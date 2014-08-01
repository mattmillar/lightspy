package edu.ucla.ee.nesl.detectmovieapp;                                                                            
public class DecisionTree 
{ 
double x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16; 

int isMovie = 0; 

DecisionTree (double x1, double x2, double x3, double x4, double x5, double x6,
double x7,  double x8,  double x9,  double x10, double x11, double x12, double
x13, double x14, double x15, double x16)

{ this.x1 = x1; this.x2 = x2; this.x3 = x3; this.x4 = x4 ; this.x5 = x5;
this.x6 = x6; this.x7 = x7; this.x8 = x8; this.x9 = x9; this.x10 = x10;
this.x11 = x11; this.x12 = x12; this.x13 = x13; this.x14 = x14; this.x15 =
x15; this.x16 = x16; } 

public boolean isMovie() 
 
{ f1(); if(isMovie == 1) return true; else return false;}
public void f1(){  if (x3<0.00692873) {f2();} else if (x3>=0.00692873) {f3();} else {isMovie = 1;}}
public void f2(){  if (x2<0.495849) {f4();} else if (x2>=0.495849) {f5();} else {isMovie = 0;}}
public void f3(){  if (x3<0.148783) {f6();} else if (x3>=0.148783) {f7();} else {isMovie = 1;}}
public void f4(){  if (x1<2.20926) {f8();} else if (x1>=2.20926) {f9();} else {isMovie = 0;}}
public void f5(){  isMovie = 1;}
public void f6(){  if (x6<15711.4) {f10();} else if (x6>=15711.4) {f11();} else {isMovie = 1;}}
public void f7(){  if (x9<375.161) {f12();} else if (x9>=375.161) {f13();} else {isMovie = 1;}}
public void f8(){  isMovie = 0;}
public void f9(){  isMovie = 1;}
public void f10(){  isMovie = 1;}
public void f11(){  if (x6<18484.8) {f14();} else if (x6>=18484.8) {f15();} else {isMovie = 0;}}
public void f12(){  if (x5<0.000115288) {f16();} else if (x5>=0.000115288) {f17();} else {isMovie = 1;}}
public void f13(){  if (x9<409.57) {f18();} else if (x9>=409.57) {f19();} else {isMovie = 1;}}
public void f14(){  isMovie = 0;}
public void f15(){  isMovie = 1;}
public void f16(){  isMovie = 0;}
public void f17(){  if (x6<17938.9) {f20();} else if (x6>=17938.9) {f21();} else {isMovie = 1;}}
public void f18(){  isMovie = 0;}
public void f19(){  if (x6<7083.84) {f22();} else if (x6>=7083.84) {f23();} else {isMovie = 1;}}
public void f20(){  if (x6<17235.6) {f24();} else if (x6>=17235.6) {f25();} else {isMovie = 1;}}
public void f21(){  isMovie = 1;}
public void f22(){  if (x1<27.1623) {f26();} else if (x1>=27.1623) {f27();} else {isMovie = 1;}}
public void f23(){  isMovie = 1;}
public void f24(){  if (x1<16.9515) {f28();} else if (x1>=16.9515) {f29();} else {isMovie = 1;}}
public void f25(){  if (x1<90.9147) {f30();} else if (x1>=90.9147) {f31();} else {isMovie = 0;}}
public void f26(){  isMovie = 1;}
public void f27(){  isMovie = 0;}
public void f28(){  isMovie = 1;}
public void f29(){  if (x6<14967.9) {f32();} else if (x6>=14967.9) {f33();} else {isMovie = 1;}}
public void f30(){  isMovie = 0;}
public void f31(){  isMovie = 1;}
public void f32(){  isMovie = 0;}
public void f33(){  if (x2<11.5515) {f34();} else if (x2>=11.5515) {f35();} else {isMovie = 1;}}
public void f34(){  isMovie = 0;}
public void f35(){  isMovie = 1;}
}