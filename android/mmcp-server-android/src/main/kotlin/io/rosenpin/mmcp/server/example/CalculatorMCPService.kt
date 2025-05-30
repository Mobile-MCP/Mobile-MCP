package io.rosenpin.mmcp.server.example

import io.rosenpin.mmcp.server.MCPServiceBase
import io.rosenpin.mmcp.server.annotations.*
import kotlinx.coroutines.delay

/**
 * Example MCP server implementation demonstrating the Mobile MCP Framework.
 * 
 * This is what a 3rd party developer would write to create an MCP server.
 * The framework automatically:
 * - Discovers all @MCPTool, @MCPResource, and @MCPPrompt methods
 * - Generates AIDL service implementations
 * - Handles parameter conversion and validation
 * - Manages async execution and callbacks
 * 
 * The developer only needs to:
 * 1. Extend MCPServiceBase
 * 2. Add @MCPServer to the class
 * 3. Add @MCPTool/@MCPResource/@MCPPrompt to methods
 * 4. Configure the service in AndroidManifest.xml
 */
@MCPServer(
    id = "com.example.calculator",
    name = "Calculator MCP Server",
    description = "Provides basic mathematical operations and calculations",
    version = "1.0.0"
)
class CalculatorMCPService : MCPServiceBase() {
    
    // ===================================================================================
    // Tool Methods - Basic Mathematical Operations
    // ===================================================================================
    
    @MCPTool(
        id = "add",
        name = "add",
        description = "Add two numbers together",
        parameters = """{
            "type": "object",
            "properties": {
                "a": {"type": "number", "description": "First number"},
                "b": {"type": "number", "description": "Second number"}
            },
            "required": ["a", "b"]
        }"""
    )
    fun add(
        @MCPParam("a") a: Double,
        @MCPParam("b") b: Double
    ): Double {
        return a + b
    }
    
    @MCPTool(
        id = "subtract",
        name = "subtract", 
        description = "Subtract second number from first number",
        parameters = """{
            "type": "object",
            "properties": {
                "a": {"type": "number", "description": "First number"},
                "b": {"type": "number", "description": "Second number"}
            },
            "required": ["a", "b"]
        }"""
    )
    fun subtract(
        @MCPParam("a") a: Double,
        @MCPParam("b") b: Double
    ): Double {
        return a - b
    }
    
    @MCPTool(
        id = "multiply",
        name = "multiply",
        description = "Multiply two numbers",
        parameters = """{
            "type": "object", 
            "properties": {
                "a": {"type": "number", "description": "First number"},
                "b": {"type": "number", "description": "Second number"}
            },
            "required": ["a", "b"]
        }"""
    )
    fun multiply(
        @MCPParam("a") a: Double,
        @MCPParam("b") b: Double
    ): Double {
        return a * b
    }
    
    @MCPTool(
        id = "divide",
        name = "divide",
        description = "Divide first number by second number",
        parameters = """{
            "type": "object",
            "properties": {
                "a": {"type": "number", "description": "Dividend"},
                "b": {"type": "number", "description": "Divisor"}
            },
            "required": ["a", "b"]
        }"""
    )
    fun divide(
        @MCPParam("a") a: Double,
        @MCPParam("b") b: Double
    ): String {
        if (b == 0.0) {
            return "Error: Division by zero"
        }
        return (a / b).toString()
    }
    
    @MCPTool(
        id = "power",
        name = "power",
        description = "Raise first number to the power of second number",
        parameters = """{
            "type": "object",
            "properties": {
                "base": {"type": "number", "description": "Base number"},
                "exponent": {"type": "number", "description": "Exponent"}
            },
            "required": ["base", "exponent"]
        }"""
    )
    fun power(
        @MCPParam("base") base: Double,
        @MCPParam("exponent") exponent: Double
    ): Double {
        return Math.pow(base, exponent)
    }
    
    // ===================================================================================
    // Async Tool Methods - Demonstrates suspend function support
    // ===================================================================================
    
    @MCPTool(
        id = "factorial",
        name = "factorial",
        description = "Calculate factorial of a number (async operation)",
        parameters = """{
            "type": "object",
            "properties": {
                "n": {"type": "integer", "description": "Number to calculate factorial for", "minimum": 0}
            },
            "required": ["n"]
        }"""
    )
    suspend fun factorial(@MCPParam("n") n: Int): String {
        if (n < 0) {
            return "Error: Factorial is not defined for negative numbers"
        }
        
        // Simulate some processing time
        delay(100)
        
        var result = 1L
        for (i in 1..n) {
            result *= i
            // Add small delay to demonstrate async nature
            if (i % 5 == 0) delay(10)
        }
        
        return "Factorial of $n is $result"
    }
    
    @MCPTool(
        id = "fibonacci",
        name = "fibonacci",
        description = "Calculate Fibonacci sequence up to n terms (async operation)",
        parameters = """{
            "type": "object",
            "properties": {
                "n": {"type": "integer", "description": "Number of terms to calculate", "minimum": 1, "maximum": 50}
            },
            "required": ["n"]
        }"""
    )
    suspend fun fibonacci(@MCPParam("n") n: Int): List<Long> {
        if (n <= 0) {
            return emptyList()
        }
        
        val sequence = mutableListOf<Long>()
        var a = 0L
        var b = 1L
        
        for (i in 0 until n) {
            sequence.add(a)
            val temp = a + b
            a = b
            b = temp
            
            // Add small delay to demonstrate async nature
            delay(20)
        }
        
        return sequence
    }
    
    // ===================================================================================
    // Resource Methods - Mathematical Constants and Formulas
    // ===================================================================================
    
    @MCPResource(
        scheme = "math",
        name = "Mathematical Constants and Formulas",
        description = "Provides access to mathematical constants and formulas",
        mimeType = "application/json"
    )
    fun getMathResource(@MCPParam("uri") uri: String): String {
        val path = uri.substringAfter("math://")
        
        return when (path) {
            "constants/pi" -> """{"name": "Pi", "symbol": "π", "value": ${Math.PI}, "description": "Ratio of circumference to diameter"}"""
            "constants/e" -> """{"name": "Euler's Number", "symbol": "e", "value": ${Math.E}, "description": "Base of natural logarithm"}"""
            "constants/golden" -> """{"name": "Golden Ratio", "symbol": "φ", "value": ${(1 + Math.sqrt(5.0)) / 2}, "description": "Divine proportion"}"""
            "formulas/area/circle" -> """{"formula": "πr²", "description": "Area of a circle", "variables": {"r": "radius"}}"""
            "formulas/area/rectangle" -> """{"formula": "l × w", "description": "Area of a rectangle", "variables": {"l": "length", "w": "width"}}"""
            "formulas/quadratic" -> """{"formula": "(-b ± √(b²-4ac)) / 2a", "description": "Quadratic formula", "variables": {"a": "coefficient of x²", "b": "coefficient of x", "c": "constant term"}}"""
            else -> """{"error": "Resource not found", "available": ["constants/pi", "constants/e", "constants/golden", "formulas/area/circle", "formulas/area/rectangle", "formulas/quadratic"]}"""
        }
    }
    
    // ===================================================================================
    // Prompt Methods - Mathematical Problem Templates
    // ===================================================================================
    
    @MCPPrompt(
        id = "word_problem",
        name = "math_word_problem",
        description = "Generate a mathematical word problem template",
        parameters = """{
            "type": "object",
            "properties": {
                "type": {"type": "string", "enum": ["addition", "subtraction", "multiplication", "division"], "description": "Type of mathematical operation"},
                "difficulty": {"type": "string", "enum": ["easy", "medium", "hard"], "description": "Difficulty level"},
                "context": {"type": "string", "description": "Context for the word problem (e.g., 'shopping', 'cooking', 'travel')"}
            },
            "required": ["type"]
        }"""
    )
    fun generateWordProblem(
        @MCPParam("type") type: String,
        @MCPParam("difficulty") difficulty: String = "medium",
        @MCPParam("context") context: String = "general"
    ): String {
        val numbers = when (difficulty) {
            "easy" -> Pair((1..10).random(), (1..10).random())
            "medium" -> Pair((10..100).random(), (10..100).random())
            "hard" -> Pair((100..1000).random(), (100..1000).random())
            else -> Pair((10..100).random(), (10..100).random())
        }
        
        val (a, b) = numbers
        
        return when (type) {
            "addition" -> when (context) {
                "shopping" -> "You bought $a apples for $2 each and $b oranges for $3 each. How much did you spend in total?"
                "cooking" -> "A recipe calls for $a cups of flour and $b cups of sugar. How many total cups of dry ingredients do you need?"
                else -> "If you have $a items and someone gives you $b more items, how many items do you have in total?"
            }
            "subtraction" -> when (context) {
                "shopping" -> "You had $a dollars and spent $b dollars. How much money do you have left?"
                "cooking" -> "You need $a cups of milk but only have $b cups. How many more cups do you need to buy?"
                else -> "If you start with $a items and give away $b items, how many items do you have left?"
            }
            "multiplication" -> when (context) {
                "shopping" -> "You want to buy $a packages, each containing $b items. How many items will you have in total?"
                "cooking" -> "If one batch of cookies requires $a ingredients and you want to make $b batches, how many total ingredients do you need?"
                else -> "If you have $a groups with $b items in each group, how many items do you have in total?"
            }
            "division" -> when (context) {
                "shopping" -> "You have $a dollars and want to buy items that cost $b dollars each. How many items can you buy?"
                "cooking" -> "You have $a cups of ingredients and need to divide them equally among $b bowls. How many cups go in each bowl?"
                else -> "If you have $a items and want to divide them equally among $b people, how many items does each person get?"
            }
            else -> "Create a mathematical problem involving the numbers $a and $b."
        }
    }
    
    @MCPPrompt(
        id = "equation_solver",
        name = "equation_solver_prompt",
        description = "Generate a prompt for solving mathematical equations",
        parameters = """{
            "type": "object",
            "properties": {
                "equation_type": {"type": "string", "enum": ["linear", "quadratic", "exponential"], "description": "Type of equation to solve"},
                "show_steps": {"type": "boolean", "description": "Whether to show solution steps"}
            },
            "required": ["equation_type"]
        }"""
    )
    fun generateEquationSolverPrompt(
        @MCPParam("equation_type") equationType: String,
        @MCPParam("show_steps") showSteps: Boolean = true
    ): String {
        val stepInstructions = if (showSteps) " Show all steps in your solution." else ""
        
        return when (equationType) {
            "linear" -> "Solve the following linear equation for x: ${generateLinearEquation()}.$stepInstructions"
            "quadratic" -> "Solve the following quadratic equation for x: ${generateQuadraticEquation()}.$stepInstructions Use the quadratic formula if needed."
            "exponential" -> "Solve the following exponential equation for x: ${generateExponentialEquation()}.$stepInstructions"
            else -> "Solve the following mathematical equation: ${generateLinearEquation()}.$stepInstructions"
        }
    }
    
    // ===================================================================================
    // Helper Methods for Prompt Generation
    // ===================================================================================
    
    private fun generateLinearEquation(): String {
        val a = (2..10).random()
        val b = (1..20).random()
        val c = (1..50).random()
        return "${a}x + $b = $c"
    }
    
    private fun generateQuadraticEquation(): String {
        val a = (1..5).random()
        val b = (-10..10).random()
        val c = (-20..20).random()
        val sign1 = if (b >= 0) "+" else ""
        val sign2 = if (c >= 0) "+" else ""
        return "${a}x² $sign1${b}x $sign2$c = 0"
    }
    
    private fun generateExponentialEquation(): String {
        val base = (2..5).random()
        val result = (10..100).random()
        return "${base}^x = $result"
    }
}