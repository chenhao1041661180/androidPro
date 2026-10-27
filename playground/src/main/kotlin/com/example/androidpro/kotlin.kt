package com.example.androidpro

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class kotlin {

}

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
fun main() {
//使用封闭同步代码的临界区
    //limitedParallelism 比newSingleThreadContext更高效，更好用，不需要手动关闭
    //一次只运行一个
//    val sys = Dispatchers.Default.limitedParallelism(1)
//    val sig = newSingleThreadContext("g")
//    val mutex = Mutex()

    runBlocking {
//        coroutineScope {
        //单线程调度器
//            withContext(sys) {
//            }
//            mutex.withLock {
//            }
        launch {
            print1()
        }

        launch {
            print3()
        }

//        launch {
            print2()
//        }
    }
//    }

//    sig.close()
}

suspend fun print1() {
    coroutineScope {
        launch {
            println("print1")
        }
//        launch {
            println("print1111")
//        }
        launch {
            println("print1111111")
        }

    }
}

suspend fun print2() {
    coroutineScope {
        println("print2")
        println("print2222")
        println("print2222222")
    }
}

suspend fun print3() {
    coroutineScope {
        println("print3")
        println("print3333")
        println("print33333333")
    }
}
