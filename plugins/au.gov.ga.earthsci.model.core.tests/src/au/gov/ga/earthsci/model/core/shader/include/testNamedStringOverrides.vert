#version 110

// Test shader that has #include directives for files in the same folder
 
uniform float u1
varying float v1;

#include include1.glsl

vec3 function1(vec3 arg1, vec4 arg2)
{
    return arg1 + arg2.xyz;
}

#include /au/gov/ga/earthsci/model/core/shader/include/include1.glsl

void main(void)
{	
	gl_FrontColor = vec4(gl_Color.rgb, gl_Color.a);

	gl_Position = gl_ModelViewProjectionMatrix * vec4(cartesian, 1.0);
}