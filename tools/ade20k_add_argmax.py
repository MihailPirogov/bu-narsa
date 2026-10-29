import flatbuffers, numpy as np
from ai_edge_litert import schema_py_generated as s
buf=open('ade20k.tflite','rb').read()
m=s.ModelT.InitFromObj(s.Model.GetRootAsModel(buf,0))
g=m.subgraphs[0]
out_idx=g.outputs[0]
# opcode
BO=s.BuiltinOperator.ARG_MAX
oc=[i for i,c in enumerate(m.operatorCodes) if max(c.builtinCode,c.deprecatedBuiltinCode)==BO]
if oc: oci=oc[0]
else:
  c=s.OperatorCodeT(); c.builtinCode=BO; c.deprecatedBuiltinCode=BO; c.version=1; m.operatorCodes.append(c); oci=len(m.operatorCodes)-1
# axis const buffer
b=s.BufferT(); b.data=np.array([3],np.int32).tobytes(); m.buffers.append(b); bi=len(m.buffers)-1
t=s.TensorT(); t.shape=[1]; t.type=s.TensorType.INT32; t.buffer=bi; t.name=b'argmax_axis'; g.tensors.append(t); ai=len(g.tensors)-1
eb=s.BufferT(); m.buffers.append(eb)
o=s.TensorT(); o.shape=[1,513,513]; o.type=s.TensorType.INT32; o.buffer=len(m.buffers)-1; o.name=b'class_map'; g.tensors.append(o); oi=len(g.tensors)-1
op=s.OperatorT(); op.opcodeIndex=oci; op.inputs=[out_idx,ai]; op.outputs=[oi]
op.builtinOptionsType=s.BuiltinOptions.ArgMaxOptions; ao=s.ArgMaxOptionsT(); ao.outputType=s.TensorType.INT32; op.builtinOptions=ao
g.operators.append(op); g.outputs=[oi]
m.metadata=None; m.signatureDefs=None
fb=flatbuffers.Builder(0); fb.Finish(m.Pack(fb),file_identifier=b'TFL3')
open('ade20k_am.tflite','wb').write(fb.Output())
