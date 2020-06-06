This code is a simple transfer algorithm which use sample transfer approach to tune concurrency value in real time. And in each sample transfer tries to probe network throughput using different algorithms line Time Series based probing, Deep Neural Network based probing, Fixed duration based probing and Random Forest based probing. 
To run these algorithm, you will need to have python3 installed with statsmodel, sklearn and numpy packages installed. 
Follow the following steps to run the transfer algorithm.
1) Copy the code to both sender side and receiver side.
2) On sender side compile SimpleSender.java and on receiver side compile SimpleReceiver.java.
3) If you don't have python3 pacakages installed on the sender side, go ahead and install those packages mentioned above.
4) On receiver side once the java file is compiled, run the code with one argument being the path of a folder where you want to save the received files. You can use the command in the following fomat. "java SimpleReceiver /dir/to/save/the/received/files/;"
5) On the sender side run the python code. You can use the following command to run the python file. "python3 convergence.py;"
6) Once both receiver and python code is running on the sender side run the Sender code. The arguments for the senders are as follows: "java SimpleSender $receiver-ip-address $folder-to-be-sent $starting-cc-value $frequency-of-throughput-update $algorithm-for-network-probng;". You can set $starting-cc-value as 1. $frequency-of-throughput-update is in seconds, so, if you want to view throughput of transfer every 100 ms, you will need to pass 0.1. And for $algorithm-for-network-porbing, you can use one of the following 'avg', 'ar', 'dnn', 'rand', you will need to know that to use 'dnn', and 'rand', you will need to have folder containing clfs which you generate by training the models. And for 'avg' and 'ar' you can use them even without any trained models.
 
