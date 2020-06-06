#!/usr/bin/env python3 -W ignore::DeprecationWarning
# -*- coding: utf-8 -*-
"""
Created on Thu May 28 11:53:06 2020

@author: hsapkota
"""
from warnings import filterwarnings
filterwarnings("ignore")
import time
import socket
from sklearn.externals import joblib
from statsmodels.tsa.ar_model import AR
import numpy as np
import os
filterwarnings('ignore')
filterwarnings('ignore', 'sklearn.externals.joblib', DeprecationWarning)
filterwarnings('ignore', 'statsmodels.tsa.ar_model.AR', FutureWarning)
filterwarnings("ignore",category=FutureWarning)



class Convergence:
    def __init__(self, model_name):
        self.model = model_name
    def find_average_thpt(self, thpt_list):
        if not thpt_list:
            return 0.
        return (1.0*sum(thpt_list))/len(thpt_list)
    def ar(self, thpt_list):
        if(len(thpt_list)<4):
            return 0.
        if(len(thpt_list)>15):
            return self.find_average_thpt(thpt_list)
        tmp = [0]+thpt_list[:-1]
        model = AR(tmp)
        start_params = [0, 0, 1]
        
        model_fit = model.fit(maxlag=1, start_params=start_params, disp=-1)
        predicted_last = model_fit.predict(len(tmp), len(tmp))[0]
        last_pt = thpt_list[-1]
        
        if( (last_pt != 0.) and abs(predicted_last - last_pt)/last_pt < 0.1):
            return predicted_last
        return 0.
    def load_clfs(self):
        self.all_clfs = {}
        for i in range(3, 16):
            self.all_clfs[i] = joblib.load("./clfs/dtns-10-%d-42-percentage-optimal.pkl"%i)#This is the path for the trained model for MLP classifier for DNN
    def get_percentage_change_thpts(self, thpt_list):
        if len(thpt_list) <= 1:
            return []
        new_thpt = []
        prev_thpt = thpt_list[0]
        for index in range(1, len(thpt_list)):
            perc = thpt_list[index] - prev_thpt
            new_thpt.append(perc / (prev_thpt+1.5))
            prev_thpt = thpt_list[index]
        return new_thpt[:16]
    def regression_train(self):
        for n in range(self.min_points, self.max_points+1):
            file_path = "./trained_regressors/regressor_%d.joblib" % n #This is the path for the trained model for random forest regressor
            if os.path.exists(file_path):
                self.regressors[n] = joblib.load(file_path)
    
    def adaptive_model(self):
        self.threshold = 10
        self.min_points = 2
        self.max_points = 15
        self.classifiers = {}
        self.regressors = {}
        self.num_of_tree = 50
        
    def classification_train(self):
        for n in range(self.min_points, self.max_points+1):
            file_path = "./trained_classifiers/classifier_%d.joblib" % n #This is the path for the trained model for random forest classifier
            if os.path.exists(file_path):
                self.classifiers[n] = joblib.load(file_path)
    def is_predictable(self, test_data):
        n = len(test_data)
        test_value = np.reshape(test_data, (1,n))
        if self.classifiers[n].predict(test_value)[0]==1 or n==self.max_points:
            return True
        else:
            return False
    def make_prediction(self, test_data):
        test_data = test_data[:self.max_points+1]
        n = len(test_data)
        test_value = np.reshape(test_data, (1,n))
        return self.regressors[n].predict(test_value)[0]
    
    def get_max_and_index(self, lis):
        max_ = lis[0]
        ind_ = 0
        for i in range(len(lis)):
            if max_ <= lis[i]:
                max_ = lis[i]
                ind_ = i
        return max_, ind_
    def find_convergence_dnn(self, thpt_list):
        threshold = 1.0
        prev_thpt_list = thpt_list
        thpt_list = self.get_percentage_change_thpts(thpt_list)
        if len(thpt_list)<3:
            return 0
        elif len(thpt_list)>=15:
            return self.find_average_thpt(thpt_list)
        i = len(thpt_list)
        y_pred = self.all_clfs[i].predict_proba([thpt_list])[0]
        max_, ind_ = self.get_max_and_index(y_pred)
        if(max_ > (threshold - 0.05*(len(thpt_list) - 2)) and ind_+2 <= i+1):
            return self.find_average_thpt(prev_thpt_list)
        return 0.0
    def find_convergence(self, thpt_list):
        if self.model == "avg":
            return 0 if len(thpt_list) < 10 else self.find_average_thpt(thpt_list)
        elif self.model == "ar":
            return 0 if len(thpt_list) < 4 else self.ar(thpt_list)
        elif self.model == "dnn":
            self.load_clfs()
            return 0 if len(thpt_list) < 3 else self.find_convergence_dnn(thpt_list)
        elif self.model == "rand":
            self.adaptive_model()
            self.regression_train()
            self.classification_train()
            if len(thpt_list) >= self.min_points and self.is_predictable(thpt_list):
                return self.make_prediction(thpt_list)
            return 0.

def thpt_parse(thpt):
    thpts = thpt.strip().split(" ")
    new_thpt = []
    for i in thpts:
        try:
            new_thpt.append(float(i))
        except:
            print("[-] Thpt value can't be converted to floating point value")
            exit(0)
    return new_thpt

def socket_():
    print("Starting")
    recv_buffer_size = 1024
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect(("localhost", 32015))
    model = sock.recv(recv_buffer_size).decode().strip()
    print(model)
    new_model = Convergence(model)
    while True:
       message  = sock.recv(recv_buffer_size).decode()
       if "done" in message:
           return
       print("Received " + message)
       instanThroughputList = list(map(float, message.split()))
       conv = new_model.find_convergence(instanThroughputList)
       print(message + ' predicted ' +  str(conv))
       output = str(conv) + "\n"
       sock.sendall(output.encode('utf-8'))

def soc_():
    recv_buffer_size = 8192
    new_model = None
    while True:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(('', 32015))
            sock.listen(1)
            print("[+] Waiting for connection at port 32015")
            c, addr = sock.accept()
            print("[+] Connected")
            while True:
                message  = c.recv(recv_buffer_size).decode()
                output = ""
                model = ""
                messageList = message.split()
                if len(messageList) > 0:
                    model = messageList[0].strip().lower() 
                if "done" in model:
                    c.close()
                    sock.close()
                    print("[+] Breaking from the inner loop.")
                    break
                if len(messageList) < 3:
                    output = "0.0\n"
                else:
                    instanThroughputList = list(map(float, messageList[1:]))
                    if not new_model or new_model != model:
                        new_model = Convergence(model)
                    output = str(new_model.find_convergence(instanThroughputList)) + "\n"
                c.sendall(output.encode('utf-8'))
                print("Received " + model + " thr:" + " ".join(messageList[1:]))
                print(model + ' predicted ' +  output)
        except socket.error as e:
            print(e)
            time.sleep(1)
if __name__ == "__main__":
    soc_()
    